# KodiScript Kotlin SDK

Interpréteur KodiScript v1.2 pour Kotlin/Spring Boot.

## 🎯 Pourquoi KodiScript ?

Vous avez déjà eu besoin d'exécuter du code dynamiquement dans votre application ? De laisser vos utilisateurs définir des règles métier sans recompiler tout le projet ? C'est exactement pour ça que KodiScript existe.

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
    implementation("com.kodi:kodi-script:1.2.1")
}
```

### Gradle Groovy

```groovy
implementation 'com.kodi:kodi-script:1.2.1'
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

| Fonction | Description |
|----------|-------------|
| `print(...)` | Affiche des valeurs |
| `toString(val)` | Convertit en string |
| `toNumber(val)` | Convertit en nombre |
| `base64Encode(str)` | Encode en Base64 |
| `base64Decode(str)` | Décode du Base64 |
| `urlEncode(str)` | Encode pour URL |
| `urlDecode(str)` | Décode une URL |
| `jsonParse(str)` | Parse du JSON |
| `jsonStringify(val)` | Sérialise en JSON |
| `toUpperCase(str)` | Majuscules |
| `toLowerCase(str)` | Minuscules |
| `length(str)` | Longueur d'une string |
| `typeOf(val)` | Retourne le type |
| `isNull(val)` | Vérifie si null |

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

// Point-virgule optionnel
let x = 1
let y = 2;  // Les deux sont valides
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
