# KodiScript Kotlin SDK

Interpréteur KodiScript v0.0.1 pour Kotlin/Spring Boot.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.issadicko/kodi-script)](https://central.sonatype.com/artifact/io.github.issadicko/kodi-script)

## 🎯 Pourquoi KodiScript ?

Vous avez déjà eu besoin d'exécuter du code dynamiquement dans votre application ? De laisser vos utilisateurs (admins) définir des règles métier sans recompiler tout le projet ? C'est exactement pour ça que KodiScript existe.

**KodiScript** est un langage de script minimaliste, pensé pour être :

- **Simple à apprendre** — Une syntaxe épurée, proche du JavaScript, que n'importe qui peut comprendre en quelques minutes
- **Léger** — Pas de dépendances lourdes, juste l'essentiel pour faire le travail
- **Sécurisé** — Exécution sandboxée, vos utilisateurs peuvent écrire des scripts sans risquer de casser votre système
- **Facile à intégrer** — Quelques lignes de code suffisent pour l'embarquer dans votre projet Kotlin/Spring Boot

Imaginez : un admin qui configure des règles de validation, un workflow qui s'adapte selon le contexte, ou des transformations de données à la volée. Tout ça devient possible sans toucher à votre code source.

## Installation

### Gradle Kotlin DSL

```kotlin
dependencies {
    implementation("io.github.issadicko:kodi-script:0.0.1")
}
```

### Gradle Groovy

```groovy
implementation 'io.github.issadicko:kodi-script:0.0.1'
```

### Maven

```xml
<dependency>
    <groupId>io.github.issadicko</groupId>
    <artifactId>kodi-script</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Utilisation

### Exécution simple

```kotlin
import com.kodi.script.KodiScript

val result = KodiScript.run("""
    let name = "Kodi"
    let version = 1.2
    print("Hello " + name)
""")

result.output.forEach { println(it) }
```

### Injection de variables

```kotlin
val variables = mapOf(
    "user" to mapOf(
        "name" to "Alice",
        "role" to "admin"
    ),
    "config" to mapOf(
        "debug" to true
    )
)

val result = KodiScript.run("""
    let greeting = "Hello " + user.name
    let status = user?.active ?: "offline"
    print(greeting)
""", variables)
```

### Builder pattern

```kotlin
val result = KodiScript.builder("""
    let greeting = customGreet("World")
    print(greeting)
""")
    .withVariable("version", 1.2)
    .registerFunction("customGreet") { args ->
        "Hello, ${args[0]}!"
    }
    .execute()
```

## Fonctions natives

### Chaînes de caractères
| Fonction | Description |
|----------|-------------|
| `print(...)` | Affiche des valeurs |
| `toString(val)` | Convertit en string |
| `toNumber(val)` | Convertit en nombre |
| `length(str)` | Longueur d'une chaîne |
| `substring(str, start, [end])` | Extrait une sous-chaîne |
| `toUpperCase(str)` | Convertit en majuscules |
| `toLowerCase(str)` | Convertit en minuscules |
| `trim(str)` | Supprime les espaces |
| `replace(str, old, new)` | Remplace du texte |
| `split(str, sep)` | Sépare en tableau |
| `join(arr, sep)` | Joint un tableau |
| `contains(str, substr)` | Vérifie si contient |
| `startsWith(str, prefix)` | Vérifie le début |
| `endsWith(str, suffix)` | Vérifie la fin |
| `indexOf(str, substr)` | Position d'une sous-chaîne |

### Math
| Fonction | Description |
|----------|-------------|
| `abs(n)` | Valeur absolue |
| `floor(n)` | Arrondi inférieur |
| `ceil(n)` | Arrondi supérieur |
| `round(n)` | Arrondi |
| `min(a, b, ...)` | Minimum |
| `max(a, b, ...)` | Maximum |
| `pow(base, exp)` | Puissance |
| `sqrt(n)` | Racine carrée |
| `sin(n)`, `cos(n)`, `tan(n)` | Trigonométrie |
| `log(n)`, `log10(n)`, `exp(n)` | Logarithmes |

### Random
| Fonction | Description |
|----------|-------------|
| `random()` | Nombre aléatoire [0, 1) |
| `randomInt(min, max)` | Entier aléatoire |
| `randomUUID()` | UUID v4 aléatoire |

### Crypto
| Fonction | Description |
|----------|-------------|
| `md5(str)` | Hash MD5 |
| `sha1(str)` | Hash SHA-1 |
| `sha256(str)` | Hash SHA-256 |

### JSON / Encodage
| Fonction | Description |
|----------|-------------|
| `jsonParse(str)` | Parse du JSON |
| `jsonStringify(val)` | Sérialise en JSON |
| `base64Encode(str)` | Encode en Base64 |
| `base64Decode(str)` | Décode du Base64 |
| `urlEncode(str)` | Encode pour URL |
| `urlDecode(str)` | Décode une URL |

### Tableaux
| Fonction | Description |
|----------|-------------|
| `sort(arr, [order])` | Trie (asc/desc) |
| `sortBy(arr, field, [order])` | Trie par champ |
| `reverse(arr)` | Inverse l'ordre |
| `size(arr)` | Taille du tableau |
| `first(arr)` | Premier élément |
| `last(arr)` | Dernier élément |
| `slice(arr, start, [end])` | Extrait une portion |

### Types
| Fonction | Description |
|----------|-------------|
| `typeOf(val)` | Retourne le type |
| `isNull(val)` | Vérifie si null |
| `isNumber(val)` | Vérifie si nombre |
| `isString(val)` | Vérifie si chaîne |
| `isBool(val)` | Vérifie si booléen |

## Intégration Spring Boot

```kotlin
@Service
class ScriptService {
    
    fun executeUserScript(script: String, context: Map<String, Any?>): ScriptResult {
        return KodiScript.builder(script)
            .withVariables(context)
            .execute()
    }
}

@RestController
@RequestMapping("/api/scripts")
class ScriptController(private val scriptService: ScriptService) {
    
    @PostMapping("/execute")
    fun execute(@RequestBody request: ScriptRequest): ScriptResult {
        return scriptService.executeUserScript(request.script, request.context)
    }
}
```

## Syntaxe KodiScript v1.2

```javascript
// Variables
let name = "Kodi"
let version = 1.2

// Null-safety
let status = user?.active ?: "offline"

// Conditions
if (version > 1.0) {
    print("Modern version")
} else {
    print("Legacy version")
}

// Return statement (arrête l'exécution et retourne la valeur)
let x = 10
if (x > 5) {
    return "grand"  // Retour anticipé
}
return "petit"

// Point-virgule optionnel
let a = 1
let b = 2;  // Les deux sont valides
```

## Tests

```bash
./gradlew test
```

## Build

```bash
./gradlew build
```

## Publication locale

```bash
./gradlew publishToMavenLocal
```

## Publication Maven Central

```bash
./publish.sh
# ou
./gradlew publishToMavenCentral
```
