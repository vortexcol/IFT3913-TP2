# Rapport de Mutation Testing – IFT3913

## 1. Contexte et objectifs

- faire **échouer le build** si le score de mutation diminue après un commit ;
- **ajouter des tests unitaires** basés sur **JUnit 5** et **Mockito** ;
- documenter les **choix de conception** (classes testées, mocks, valeurs simulées, configuration de PIT et de GitHub Actions) ;
- intégrer un **élément d’humour** dans la suite de test (Rickroll) lorsque la CI échoue.

L’intégration se fait dans un projet Maven multi-modules, comprenant notamment les modules `core` et `web-api`.

---

## 2. Choix des classes testées

Nous avons choisi deux classes du cœur de GraphHopper :

- `com.graphhopper.routing.weighting.SpeedWeighting` (module `core`)
- `com.graphhopper.routing.weighting.QueryGraphWeighting` (module `core`)

Ces classes sont au centre du calcul de coût des routes, et donc critiques pour la qualité logicielle globale.

### 2.1. SpeedWeighting

`SpeedWeighting` implémente l’interface `Weighting` et encapsule :

- une valeur encodée de vitesse (`DecimalEncodedValue speedEnc`) ;
- un fournisseur de coûts de virage (`TurnCostProvider turnCostProvider`) ;
- la logique de calcul de poids d’une arête en fonction de sa distance et de la vitesse ;
- la gestion des **u-turns** via `TurnCostStorage` et un coût minimal de demi-tour (`uTurnCosts`).

Comportements importants testés :

- si la vitesse est **0**, le poids doit être **+∞** :  
  `speed == 0 => weight = Double.POSITIVE_INFINITY`
- si `reverse == true`, la vitesse utilisée doit être la **vitesse reverse** (`getReverse`) et non la vitesse forward ;
- `calcMinWeightPerDistance()` doit utiliser `speedEnc.getMaxStorableDecimal()` pour calculer un poids minimal ;
- les **coûts de virage** doivent respecter :
  - pour un u-turn : `max(storedCost, uTurnCost)` ;
  - pour un virage normal : `storedCost` tel quel ;
- `hasTurnCosts()` doit refléter le constructeur utilisé (avec ou sans `TurnCostProvider`).

Ces comportements sont sensibles aux mutations classiques (inversion de conditions, modification de constantes, etc.) et sont donc excellents candidats pour le mutation testing.

### 2.2. QueryGraphWeighting

`QueryGraphWeighting` est un wrapper autour d’un `Weighting` existant, utilisé lorsque l’on calcule des itinéraires sur un `QueryGraph`. Il doit gérer :

- les **nœuds virtuels** (créés par les requêtes pour représenter des points intermédiaires) ;
- les **arêtes virtuelles** ;
- les **u-turns aux nœuds virtuels** (qui doivent être interdits ou fortement pénalisés) ;
- le mapping entre arêtes virtuelles et arêtes originales via un `BaseGraph` et un `IntArrayList closestEdges`.

Comportements testés :

- si `viaNode` est un **nœud virtuel** et que `inEdge == outEdge`, le coût de virage doit être **+∞** ;
- si `viaNode` est un nœud virtuel et qu’il ne s’agit pas d’un u-turn, le coût doit être **0** ;
- pour les nœuds non virtuels, `calcTurnWeight` et `calcTurnMillis` doivent **déléguer** au `Weighting` interne en sélectionnant les arêtes originales pertinentes ;
- `calcEdgeWeight` et `calcEdgeMillis` doivent être purement délégués au weighting interne ;
- `hasTurnCosts()`, `getName()` et `toString()` doivent simplement refléter l’état du `Weighting` interne.

---

## 3. Choix des classes simulées (Mocks Mockito)

Pour isoler la logique de `SpeedWeighting` et `QueryGraphWeighting`, nous avons simulé plusieurs dépendances à l’aide de Mockito :

- `DecimalEncodedValue` : permet de fournir des vitesses arbitraires (forward/reverse) et un `maxStorableDecimal`.
- `EdgeIteratorState` : représente une arête du graphe, utilisée pour fournir la distance et la vitesse.
- `TurnCostStorage` : stocke les coûts de virage entre arêtes, nécessaire pour tester les u-turns et virages normaux.
- `TurnCostProvider` : utilisé pour vérifier le comportement de `hasTurnCosts()` et la délégation des coûts de virage.
- `BaseGraph` : utilisé dans `QueryGraphWeighting` pour déterminer les ids de nœuds et d’arêtes virtuels, ainsi que pour récupérer les arêtes originales.
- `Weighting` : dans `QueryGraphWeighting`, le weighting interne est mocké pour vérifier que les appels de calcul de poids sont correctement délégués.

Ces mocks nous permettent de :

- tester des cas limites sans construire un graphe complet ;
- éviter les I/O ou la configuration lourde de GraphHopper ;
- se concentrer sur la **logique pure** (conditions, calculs, délégation).

---

## 4. Définition des scénarios et valeurs simulées

### 4.1. Scénarios pour SpeedWeighting

Exemples de scénarios couverts dans `SpeedWeightingTest` :

1. **Vitesse nulle**  
   ```java
   when(edgeState.get(speedEnc)).thenReturn(0.0);
   double weight = weighting.calcEdgeWeight(edgeState, false);
   assertEquals(Double.POSITIVE_INFINITY, weight);
   ```
   Permet de tuer des mutants qui changeraient la condition `speed == 0` ou le retour de `POSITIVE_INFINITY`.

2. **Vitesse reverse utilisée quand reverse=true**  
   ```java
   when(edgeState.getReverse(speedEnc)).thenReturn(10.0);
   when(edgeState.getDistance()).thenReturn(1000.0);
   double weight = weighting.calcEdgeWeight(edgeState, true);
   assertEquals(100.0, weight, 1e-6);
   verify(edgeState, never()).get(speedEnc);
   verify(edgeState, times(1)).getReverse(speedEnc);
   ```

3. **Coût minimal par distance basé sur la vitesse max stockable**  
   ```java
   when(speedEnc.getMaxStorableDecimal()).thenReturn(20.0);
   assertEquals(1.0 / 20.0, weighting.calcMinWeightPerDistance(), 1e-9);
   ```

4. **Gestion des u-turns vs virages normaux**  
   ```java
   double uTurnCost = 5.0;
   when(turnCostStorage.get(turnCostEnc, edgeId, viaNode, edgeId)).thenReturn(1.0);
   // U-turn
   assertEquals(5.0, weighting.calcTurnWeight(edgeId, viaNode, edgeId), 1e-9);
   // Virage normal
   when(turnCostStorage.get(turnCostEnc, inEdge, viaNode, outEdge)).thenReturn(2.5);
   assertEquals(2.5, weighting.calcTurnWeight(inEdge, viaNode, outEdge), 1e-9);
   ```

5. **hasTurnCosts() selon le constructeur**  
   - constructeur par défaut → `false`  
   - constructeur avec `TurnCostProvider` → `true`

### 4.2. Scénarios pour QueryGraphWeighting

Dans `QueryGraphWeightingTest`, nous simulons un `BaseGraph` avec :

- `firstVirtualNodeId = graph.getNodes()` (par ex. 100)
- `firstVirtualEdgeId = graph.getEdges()` (par ex. 200)

Nous définissons des ids comme « virtuels » lorsqu’ils sont ≥ à ces valeurs.

Scénarios :

1. **U-turn sur un nœud virtuel**  
   ```java
   int virtualNode = 120;
   int virtualEdge = 210;
   double w = qgw.calcTurnWeight(virtualEdge, virtualNode, virtualEdge);
   assertEquals(Double.POSITIVE_INFINITY, w);
   ```

2. **Non U-turn sur un nœud virtuel**  
   ```java
   int inEdge = 210;
   int outEdge = 212;
   double w = qgw.calcTurnWeight(inEdge, virtualNode, outEdge);
   assertEquals(0.0, w);
   ```

3. **Virage normal sur nœud non virtuel**  
   ```java
   int viaNode = 20;
   when(innerWeighting.calcTurnWeight(inEdge, viaNode, outEdge)).thenReturn(4.5);
   assertEquals(4.5, qgw.calcTurnWeight(inEdge, viaNode, outEdge));
   ```

4. **Délégation de calcEdgeWeight / calcEdgeMillis**  
   ```java
   when(innerWeighting.calcEdgeWeight(edgeState, false)).thenReturn(12.0);
   assertEquals(12.0, qgw.calcEdgeWeight(edgeState, false));

   when(innerWeighting.calcEdgeMillis(edgeState, true)).thenReturn(777L);
   assertEquals(777L, qgw.calcEdgeMillis(edgeState, true));
   ```

5. **Délégation de hasTurnCosts() et getName()**  
   - `when(innerWeighting.hasTurnCosts()).thenReturn(true/false);`  
   - `when(innerWeighting.getName()).thenReturn("speed");`  

Ces tests couvrent les principaux chemins d’exécution, ce qui est idéal pour le mutation testing.

---

## 5. Intégration de PIT (Mutation Testing)

### 5.1. Choix du module : web-api

Lors de l’exécution de PIT sur tous les modules, nous avons constaté que :

- le module `web-api` contient des tests JUnit 5 reconnus par PIT ;  
- le module `core` n’a, dans notre version, **aucune classe de test détectée** par PIT, entraînant un score constant de **0%**.

Afin d’obtenir un signal significatif sur la qualité des tests, PIT a été configuré pour **analyser le module `web-api`** (packages `com.graphhopper.resources.*`), qui présente un score de mutation non nul (environ 36 % dans notre environnement).

### 5.2. Configuration Maven

PIT est configuré dans le `pom.xml` via :

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.15.8</version>

    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
        </dependency>
    </dependencies>

    <configuration>
        <targetClasses>
            <param>com.graphhopper.resources.*</param>
        </targetClasses>
        <targetTests>
            <param>**/*Test</param>
        </targetTests>
        <outputFormats>
            <param>HTML</param>
            <param>XML</param>
        </outputFormats>
    </configuration>
</plugin>
```

PIT est ensuite lancé via :

```bash
mvn -pl web-api -am org.pitest:pitest-maven:mutationCoverage
```

### 5.3. Script de vérification du score

Un script Bash `scripts/check-mutation-score.sh` automatise :

1. l’exécution de PIT sur `web-api` ;
2. l’extraction du score de mutation depuis `web-api/target/pit-reports/index.html` ;
3. la comparaison de ce score avec une baseline dans `mutation-baseline.txt`.

Exemple de `mutation-baseline.txt` :

```txt
MIN_MUTATION_SCORE=30.0
```

Si le score courant est inférieur à cette valeur, le script affiche un message explicite et retourne un code d’erreur (`exit 1`), ce qui fait échouer le job dans GitHub Actions.

---

## 6. Intégration GitHub Actions et Rickroll

### 6.1. Workflow CI

Le workflow GitHub Actions effectue notamment les étapes suivantes :

1. Checkout du dépôt ;
2. Installation de Java et du cache Maven ;
3. Compilation et exécution des tests ;
4. Exécution de `scripts/check-mutation-score.sh` pour lancer PIT et vérifier la baseline ;
5. Si ce job échoue, déclenchement d’un job **Rickroll**.

Schéma simplifié :

```text
Code push
   ↓
Build & Tests (mvn test)
   ↓
PIT (web-api) + check-mutation-score.sh
   ↓                ↓
Score OK ≥ baseline Score < baseline
   ↓                ↓
SUCCESS        FAILURE + Rickroll 🎵
```

### 6.2. Rickroll

Le Rickroll est implémenté via une action réutilisable (par ex. `random-rickroll`) déclenchée uniquement si le job de mutation testing échoue. Cela apporte une touche humoristique tout en rendant l’échec très visible pour le développeur.

---
