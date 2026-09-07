# Minimal entwickeln

Implementiere oder überarbeite die genannten Dateien/Bereiche — Feature, Refactor oder Fix.

**Ziel:** weniger Code, einfacher als jetzt. Konzepte sind gut — aber **einheitlich im Projekt**: nicht drei verschiedene Wege für dasselbe Problem. Lieber überall dieselbe (auch suboptimale) Lösung als an einer Stelle „besser“ und anderswo anders — später kann man **projektweit** verbessern. Intelligent, wenn das Umständliches vermeidet — kein Show-off.

**Verhalten:** bei Refactor/Fix das Bestehende absichern (Tests). Bei neuen Features das **gewünschte** neue Verhalten umsetzen und testen — nicht heimlich anders als spezifiziert.

## Regeln

- **Zuerst im Projekt nachschauen** — ähnliche Endpoints, Queries, DTOs, Tests; dortiges Muster übernehmen, nicht eigenes erfinden
- **Minimaler Diff** — nur was der Auftrag braucht
- **Ein Konzept pro Problem** — gleiches Muster wiederverwenden; kein zweiter Parallelweg (extra Query, extra Mapper, extra Endpoint)
- **Gerade Datenwege** — SQL → `RowMapper`/`jdbcTemplate.query` → DTO in einem Schritt; Mapping nicht im `RestHandler` nachbauen
- **Schlanke Modelle** — `@Value`-DTOs bleiben dumm; keine Logik, keine Factory außer bewusst neue/leere Instanzen
- **Kein doppelter State** — dieselben Daten nicht in mehreren Feldern/Flags spiegeln
- **Keine parallele Struktur** — kein zweiter Zugriffsweg auf dieselben Daten (z. B. extra Lookup-Map neben der Query-Liste); bestehende Collection/Query nutzen
- **Keine Einmal-Hilfsmethoden** — Logik die nur einmal vorkommt inline lassen
- **Keine Extra-API für Setup** — wenn Constructor / `@PostConstruct` reicht
- **Typen ehrlich** — kein `@SuppressWarnings`; sauberes Modell statt Null-Check-Kaskaden
- **Kein defensives Wegfiltern** — kaputte Eingaben nicht still reparieren; klares Verhalten + Test für den echten Vertrag
- **Keine Defense-in-Depth** — vorsorgliche Guards/Checks entfernen, die nur als Absicherung da sind und im normalen Betrieb nie eintreten können (redundante Null-Checks, unreachable States, doppelte Validierung desselben Vertrags); echte Randfälle gehören ins Modell oder in Tests
- **Projekt-Stil** — Nachbarcode (`RestHandler`, `CardanoDbSyncService`, DTOs) als Referenz; generierte/auto-erzeugte Dateien nicht handeditieren
- **Kein lokales „besser“** — keine neue Architektur nur in einem Modul; Verbesserungen erst anstoßen, wenn sie sich für das ganze Projekt eignen
- **Optional & Streams ja** — gerne für Pipelines (`map`, `filter`, `flatMap`, `collect`, `toList` …). `ifPresent` / `forEach` ok bei **Einzeiler** oder Methodenreferenz. **Nicht** als Hülle für mehrzeilige Logik — dafür `if` / `for`
- **Block-Kommentare** — bei längerer oder mehrstufiger Logik kurze `//`-Kommentare vor jedem inhaltlichen Block (wie in `CardanoDbSyncService`): was der nächste Abschnitt tut, nicht jede Zeile erklären. Beim Refactor **beibehalten**; fehlen sie bei mehreren klar getrennten Schritten, ergänzen. Kein Ersatz für JavaDoc an public API

## Vorgehen

1. **Im Projekt vergleichen** — wie wird dasselbe Problem woanders gelöst? (Suche, ähnliche Endpoints/Queries/DTOs)
2. Verstehen, was der Code tun soll (Anforderung, Tests)
3. **Vereinfachen** — zuerst entfernen: doppelter State, Parser, Zwischen-APIs, defensive Schichten, **abweichende Patterns**, **Defense-in-Depth-Checks**
4. Framework/Standardweg nutzen — **bevorzugt den, den das Projekt schon nutzt** (`JdbcTemplate`, `@Cacheable`, `@Value`-DTOs, dünner `RestHandler`)
5. Verhalten: Refactor → unverändert lassen, bis Tests grün; Feature → spezifiziertes neues Verhalten + Tests
6. Betroffene Tests ausführen und grün machen
7. Kurz berichten: an welchem Projekt-Muster orientiert, was vereinfacht wurde, welche Tests was absichern

## Statt → Lieber

| Statt | Lieber |
|-------|--------|
| Mapping in Handler **und** Service | ein Weg: Query + RowMapper im Service, Handler nur delegiert |
| Extra Lookup/Map für dieselben Keys | bestehende Liste/Query |
| Validierung im DTO-Konstruktor | schlankes `@Value`-DTO; Vertrag in Tests |
| Parse-Fehler → leerer Default | fail fast |
| Tests für korrupte/unrealistische Eingaben | Fixture des **echten** Formats + Roundtrip |
| Null-Checks überall | Modell/Lifecycle so wählen, dass Checks überflüssig werden |
| Defense-in-Depth (redundante Guards „nur für den Fall“) | einen klaren Vertrag; echte Randfälle testen, nicht an jeder Stelle absichern |
| Cleverness um der Cleverness willen | direkter Code; Abstraktion nur wenn sie wirklich vereinfacht |
| Neue Abstraktion „für später“ | YAGNI |
| Hier „eleganter“, woanders anders | überall dasselbe Muster wie im Projekt — auch wenn nicht perfekt |
| `ifPresent(x -> { … viele Zeilen … })` / `forEach(x -> { … viele Zeilen … })` | `if` / `for` |
| Stream nur als äußere Schale um Imperativ | durchgängige Pipeline oder klassische Schleife |
| Kommentare wegrationalisieren oder weglassen | Block-`//` vor logischen Schritten beibehalten/ergänzen |

## Checkliste vor Abgabe

- Netto weniger Code?
- Passt zum **bestehenden Projekt-Muster** (Referenz genannt)?
- Kein zweiter Parallelweg für dasselbe Problem?
- Keine zweite Map/Struktur für dieselben Keys/Objekte?
- Defense-in-Depth-Checks entfernt, die im Normalfall nie greifen?
- Verhalten passt zum Auftrag (Refactor: Tests grün; Feature: spezifiziert + getestet)?
- API/SQL: Fixture oder Roundtrip, wo ein Vertrag besteht?
- Kein `@SuppressWarnings`, kein stillschweigendes Fallback?
- Längere Methoden: Block-Kommentare vor den Schritten (Projekt-Stil)?
