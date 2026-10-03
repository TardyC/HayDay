# 🌾 HayDay – farm-plugin til Minecraft

Et komplet **Hay Day**-inspireret farmspil til din Minecraft-server: marker, dyr, produktionsbygninger,
silo og lade, ordretavle, vejbod, avis, levels, hologrammer og animationer. Alt er på dansk og kan konfigureres.

> Lavet til den nyeste Minecraft (bygget mod Spigot-API 1.21 og virker på 1.21.x og 26.x – Spigot, Paper og Purpur).
> Kræver Java 21 eller nyere.

---

## ✨ Funktioner

| | |
|---|---|
| 🌱 **Marker** | Køb en *Mark* i butikken, placér den på jorden og plant hvede, gulerødder, kartofler, sukkerroer, græskar, søde bær og meloner. Afgrøderne gror synligt blok for blok. |
| 🐔 **Dyr** | Hønsehus, kostald, svinesti og fårefold – med rigtige (fredelige) dyr der står på bygningen. Fodr dem med foder fra foderfabrikken. |
| 🏭 **Produktion** | Foderfabrik, bageri, sukkermølle, mejeri, væveri og saftpresse med 30+ opskrifter, produktionskø og pladser du kan købe. |
| 🏚️ **Silo & lade** | Afgrøder i siloen, produkter i laden. Begge kan opgraderes. Sælg varer direkte fra lageret. |
| 📋 **Ordretavle** | Tilfældige ordrer baseret på dit level. Lever varer for penge og XP, eller kassér ordren. |
| 🛒 **Vejbod & avis** | Sæt dine varer til salg for andre spillere. Andre kan købe i din vejbod (også når du er offline) eller finde tilbuddene i **Avisen**. Du henter pengene i vejboden – præcis som i Hay Day. |
| ⭐ **Levels** | XP fra høst, produktion og ordrer. Hvert level låser nye afgrøder, bygninger, opskrifter og marker op. |
| 💬 **Hologrammer** | Over hver mark og bygning: vækst-bar, nedtælling, kø og status. Plus admin-hologrammer og top-lister. |
| 🎬 **Animationer** | Varer hopper op og flyver ind til dig, "+2 Hvede"-tekster svæver op, mønt-regn ved ordrer, fyrværkeri ved level up, frø der falder ned i jorden og vippende ikoner over klare marker og bygninger. |
| 🎨 **ItemsAdder** (valgfrit) | Hay Day-inspirerede menu-baggrunde, tegnede knapper og ikoner i hologrammer. |
| 📦 **ProtocolLib** (valgfrit) | Minecrafts rigtige "saml op"-animation når du høster og henter varer. |
| 💰 **Økonomi** | Bruger Vault (fx EssentialsX) – eller HayDays egne mønter. |
| 📊 **PlaceholderAPI** | `%hayday_level%`, `%hayday_top_name_1%` og mange flere. |

---

## 📥 Installation

1. Download `HayDay-x.y.z.jar` (fra *Actions* → seneste build → *Artifacts*, eller fra *Releases*).
2. Læg den i `plugins/`-mappen og genstart serveren.
3. (Valgfrit) Installér **Vault** + et økonomi-plugin, **PlaceholderAPI**, **ProtocolLib** og **ItemsAdder**.
4. Skriv `/hayday` i spillet – første gang får du startpakken (3 marker og lidt hvede).

### ItemsAdder-menuer
Når ItemsAdder er installeret, kopierer HayDay automatisk sit indhold til
`plugins/ItemsAdder/contents/hayday/` og kører `/iazip`. Når spillerne har den nye resourcepack,
får alle menuer træ-rammer, bånd-titler, kasser og tegnede knapper. Uden ItemsAdder bruges almindelige menuer.

Placeringen af baggrunden kan finjusteres med `itemsadder.texture-offset` og `itemsadder.title-offset` i `config.yml`.
Teksturerne kan tegnes om med `python3 tools/generate_textures.py` (kræver Pillow).
Al grafik er original og tegnet til dette projekt – der bruges ingen grafik fra Hay Day/Supercell.

---

## 🎮 Sådan spiller man

* **Mark:** Højreklik på en tom mark for at vælge afgrøde. Højreklik på en klar mark for at høste.
  **Shift + højreklik** høster alle dine klare marker i nærheden. **Shift-klik** på en afgrøde i menuen planter den på alle tomme marker i nærheden.
* **Bygning:** Højreklik for at hente færdige varer og åbne produktionsmenuen.
* **Fjern:** Shift + slå på din tomme mark eller bygning (med tom kø) for at samle den op igen.
* **Hay Day-stil:** Plantning bruger 1 afgrøde fra siloen, og høsten giver 2. Har du ingen, køber du frø.

---

## ⌨️ Kommandoer

Alias: `/hd`, `/farm`, `/gaard`

| Kommando | Beskrivelse |
|---|---|
| `/hayday` | Hovedmenuen |
| `/hayday silo` · `lade` | Lageret (klik på en vare for at sælge) |
| `/hayday ordrer` | Ordretavlen |
| `/hayday butik` | Køb marker og bygninger |
| `/hayday vejbod [spiller]` | Din vejbod – eller besøg en andens |
| `/hayday avis` | Alle tilbud fra andre spillere |
| `/hayday profil [spiller]` · `top` | Profil og top-liste |
| `/hayday admin give/take <spiller> <vare> <antal>` | Giv/fjern varer i lageret |
| `/hayday admin item <spiller> <mark\|bygning> [antal]` | Giv en mark/bygning som item |
| `/hayday admin xp/level/coins <spiller> <værdi>` | XP, level og penge |
| `/hayday admin reset <spiller> confirm` | Nulstil en spiller |
| `/hayday admin info · fjern · faerdig` | Mark/bygning du kigger på |
| `/hayday admin reload` | Genindlæs alle filer |
| `/hayday holo create <navn> [tekst]` | Tekst-hologram |
| `/hayday holo top <navn>` | Top-liste-hologram |
| `/hayday holo addline · setline · removeline · command · move · tp · delete · list` | Redigér hologrammer |

`/hayday holo command <navn> hayday ordrer` gør et hologram klikbart, fx et "Ordretavle"-skilt.

## 🔑 Permissions

| Permission | Standard | Beskrivelse |
|---|---|---|
| `hayday.use` | alle | Spille HayDay |
| `hayday.admin` | op | Admin-kommandoer (giver også de to nedenfor) |
| `hayday.bypass` | op | Bruge/fjerne andres marker og bygninger, ingen mark-grænse |
| `hayday.hologram` | op | Oprette og redigere hologrammer |

## 📊 PlaceholderAPI

`%hayday_level%` `%hayday_xp%` `%hayday_xp_needed%` `%hayday_xp_progress%` `%hayday_coins%`
`%hayday_fields%` `%hayday_fields_max%` `%hayday_fields_ready%` `%hayday_products_ready%`
`%hayday_silo_used%` `%hayday_silo_capacity%` `%hayday_barn_used%` `%hayday_barn_capacity%`
`%hayday_orders_ready%` `%hayday_rank%` `%hayday_top_name_<n>%` `%hayday_top_level_<n>%` `%hayday_top_xp_<n>%`

---

## ⚙️ Konfiguration

| Fil | Indhold |
|---|---|
| `config.yml` | Økonomi, verdener, levels, marker, lager, ordrer, vejbod, hologrammer, animationer, ItemsAdder og ikoner |
| `items.yml` | Alle varer og afgrøder (navn, ikon, salgspris, vækstid, vækststadier …) |
| `buildings.yml` | Bygninger, dyr og opskrifter |
| `messages.yml` | Alle beskeder |
| `holograms.yml` | Admin-hologrammer (gemmes automatisk) |

Data gemmes i `plugins/HayDay/data/` (marker, bygninger, vejbod) og `plugins/HayDay/players/`.
Hologrammer, dyr og animationer bruger ikke-persistente entities, så der aldrig ligger "døde" hologrammer tilbage i verdenen.

---

## 🛠️ Byg selv

```bash
mvn package
# -> target/HayDay-1.0.0.jar
```

GitHub Actions bygger automatisk jar-filen ved hvert push (se fanen *Actions*). Et tag som `v1.0.0` laver en release med jar-filen.

## 📁 Struktur

```
src/main/java/dev/tardyc/hayday/
├── HayDayPlugin.java        Main-klassen
├── command/                 /hayday
├── config/                  config.yml + messages.yml
├── economy/                 Vault eller egne mønter
├── gui/                     Alle menuer
├── hologram/                Hologrammer (TextDisplay) og svævende ikoner
├── hook/                    ItemsAdder, ProtocolLib, PlaceholderAPI og ikoner
├── listener/                Events og beskyttelse af marker/bygninger
├── manager/                 Gård, marked, ordrer, levels, animationer, lager …
├── model/                   Data-klasser
├── registry/                items.yml og buildings.yml
└── util/                    Hjælpere
tools/generate_textures.py   Tegner ItemsAdder-teksturerne
```
