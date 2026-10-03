# 🌾 HayDay – farm-plugin til Minecraft

Et komplet **Hay Day**-inspireret farmspil til din Minecraft-server: hver spiller får sin **egen ø** med stuehus og marker,
man kan **besøge hinanden**, og der er dyr, produktionsbygninger, silo og lade, ordretavle, skib, vejbod, avis, levels,
hologrammer og animationer. Alt er på dansk og kan konfigureres.

> Lavet til den nyeste Minecraft (bygget mod Spigot-API 1.21 og virker på 1.21.x og 26.x – Spigot, Paper og Purpur).
> Kræver Java 21 eller nyere.

---

## ✨ Funktioner

| | |
|---|---|
| 🏝️ **Egen ø** | Hver spiller får sin egen ø i HayDay-verdenen: grønt græs, sandstrand, turkis hav, et rødt stuehus med røg fra skorstenen, træer, blomster og de første marker. Kun du kan bygge på din ø. |
| 👋 **Besøg** | Besøg andres gårde fra menuen, avisen eller med `/hayday besoeg <spiller>`. Køb i deres vejbod, hjælp med at fylde deres skib (du får belønningen) og giv gården et ❤ like. Ejeren kan se hvem der er på besøg. |
| 🔒 **Venner og adgang** | Vælg om gården er åben for alle, kun for venner eller lukket. Tilføj venner, forbyd plageånder og send gæster hjem. Giv gården sit eget navn og sæt dit eget ankomststed. |
| 🌱 **Marker** | Køb en *Mark* i butikken, placér den på jorden og plant hvede, gulerødder, kartofler, sukkerroer, græskar, søde bær og meloner. Afgrøderne gror synligt blok for blok. |
| 🐔 **Dyr** | Hønsehus, kostald, svinesti og fårefold – med rigtige (fredelige) dyr der står på bygningen. Fodr dem med foder fra foderfabrikken. |
| 🏭 **Produktion** | Foderfabrik, bageri, sukkermølle, mejeri, væveri og saftpresse med 30+ opskrifter, produktionskø og pladser du kan købe. |
| 🏚️ **Silo & lade** | Afgrøder i siloen, produkter i laden. Begge kan opgraderes. Sælg varer direkte fra lageret. |
| 📋 **Ordretavle** | Tilfældige ordrer baseret på dit level. Lever varer for penge og XP, eller kassér ordren. |
| ⚓ **Skibet** | Et skib lægger til kaj med 9 kasser. Fyld dem med varer for penge og XP, og send skibet afsted for en stor bonus. Byg en **Havn**, så ligger der en rigtig båd ved kajen, når skibet er i havn. |
| 🛒 **Vejbod & avis** | Sæt dine varer til salg for andre spillere. Andre kan købe i din vejbod (også når du er offline) eller finde tilbuddene i **Avisen**. Du henter pengene i vejboden – præcis som i Hay Day. |
| ⭐ **Levels** | XP fra høst, produktion og ordrer. Hvert level låser nye afgrøder, bygninger, opskrifter og marker op. |
| 💬 **Hologrammer** | Over hver mark og bygning: vækst-bar, nedtælling, kø og status. Plus admin-hologrammer og top-lister. |
| 🎬 **Animationer** | Varer hopper op og flyver ind til dig, "+2 Hvede"-tekster svæver op, mønt-regn ved ordrer, fyrværkeri ved level up, frø der falder ned i jorden og vippende ikoner over klare marker og bygninger. |
| 🎨 **Egen resourcepack** | Træ-rammer og bånd-titler på alle menuer (butik, silo, skib …), 15 egne item-ikoner (foder, smør, ost, juice, sweater …), ikoner i hologrammer og chat. Pluginet bygger, hoster og sender pakken selv – eller bruger ItemsAdder. |
| 👤 **Spillerfiler** | Hver spiller har sin egen fil med profil, lager, ordrer, skib og en oversigt over gården. |
| 🛠️ **Admin** | Over 15 admin-kommandoer – også til spillere der er offline. |
| 📦 **ProtocolLib** (valgfrit) | Minecrafts rigtige "saml op"-animation når du høster og henter varer. |
| 💰 **Økonomi** | Bruger Vault (fx EssentialsX) – eller HayDays egne mønter. |
| 📊 **PlaceholderAPI** | `%hayday_level%`, `%hayday_top_name_1%` og mange flere. |

---

## 📥 Installation

1. Download `HayDay-x.y.z.jar` (fra *Actions* → seneste build → *Artifacts*, eller fra *Releases*).
2. Læg den i `plugins/`-mappen og genstart serveren.
3. (Valgfrit) Installér **Vault** + et økonomi-plugin, **PlaceholderAPI**, **ProtocolLib** og **ItemsAdder**.
4. Skriv `/hayday` i spillet – første gang får du din egen ø med stuehus, 3 marker og lidt hvede, og bliver sendt derhen.

HayDay laver selv verdenen `hayday` med øerne første gang serveren starter (tager et øjeblik). Midt i verdenen ligger
**torvet**, hvor et hologram viser vej til de andre gårde.

### 🎨 Resourcepacken
HayDay har sin egen resourcepack med menu-baggrunde, item-ikoner og ikoner. Der er to måder at bruge den på
(`resource-pack.mode` i `config.yml`):

* **Uden ItemsAdder** (`own`): Pluginet bygger `plugins/HayDay/HayDay-resourcepack.zip`, hoster den selv på
  port **8163** og sender den til spillerne når de logger ind. Sæt `resource-pack.host.address` til din servers
  IP/domæne og åbn porten – eller upload zip-filen et andet sted og skriv adressen i `resource-pack.url`.
  Sæt `required: true` hvis alle *skal* have pakken (så bruger hologrammerne også ikonerne).
* **Med ItemsAdder** (`itemsadder`, vælges automatisk): Indholdet kopieres til
  `plugins/ItemsAdder/contents/hayday/`, og `/iazip` køres automatisk.

Spillere uden pakken får almindelige menuer og Minecraft-ikoner, så intet ser forkert ud.
`/hayday pakke` sender pakken igen, og `/hayday admin pakke` viser status.
Grafikken kan tegnes om med `python3 tools/generate_pack.py` (kræver Pillow).
Al grafik er original og tegnet til dette projekt – der bruges ingen grafik fra Hay Day/Supercell.

### 🏝️ Øerne
* Øerne ligger i et gitter i havet (48×48 blokke med 24 blokke vand imellem – kan ændres i `config.yml` før verdenen laves).
* Marker og bygninger kan kun placeres på din egen ø (`islands.farm-only-on-island`). Admins med `hayday.bypass` kan alt.
* Vand, lava, ild, stempler, eksplosioner og træer kan ikke gå over på en anden ø. Ingen monstre, ingen vilde dyr, intet regnvejr og ingen PvP (kan slås til).
* Lukkede øer kan ikke betrædes – heller ikke med ender pearls eller båd.
* Verdenen kan også bruges med Multiverse/bukkit.yml: `generator: HayDay`.
* Vil du ikke bruge øer, så sæt `islands.enabled: false` – så placeres marker og bygninger frit som før.

---

## 🎮 Sådan spiller man

* **Mark:** Højreklik på en tom mark for at vælge afgrøde. Højreklik på en klar mark for at høste.
  **Shift + højreklik** høster alle dine klare marker i nærheden. **Shift-klik** på en afgrøde i menuen planter den på alle tomme marker i nærheden.
* **Bygning:** Højreklik for at hente færdige varer og åbne produktionsmenuen.
* **Fjern:** Shift + slå på din tomme mark eller bygning (med tom kø) for at samle den op igen.
* **Hay Day-stil:** Plantning bruger 1 afgrøde fra siloen, og høsten giver 2. Har du ingen, køber du frø.
* **På besøg:** Højreklik på vejboden for at købe, på havnen for at hjælpe med skibet, og på skiltet ved stranden for at like gården.

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
| `/hayday skib` | Skibets kasser |
| `/hayday pakke` | Hent resourcepacken igen |
| `/hayday hjem` · `torv` | Tag hjem til din ø · til torvet |
| `/hayday besoeg [spiller]` | Besøgsmenuen – eller besøg en bestemt spiller |
| `/hayday gaard` | Min gård: adgang, navn, hjem, venner og besøgende |
| `/hayday gaard navn <navn>` · `adgang <alle\|venner\|ingen>` · `saethjem` | Gårdens indstillinger |
| `/hayday ven [tilfoej\|fjern] <spiller>` | Venner (kan altid besøge dig) |
| `/hayday like` | Like gården du står på |
| `/hayday smidud` · `forbyd` · `tillad <spiller>` | Send en gæst hjem · forbyd/tillad besøg |
| `/hayday profil [spiller]` · `top` | Profil og top-liste |
| `/hayday admin spiller <spiller>` | Alt om en spiller (level, penge, lager, gård, skib, vejbod) |
| `/hayday admin lager <spiller>` | Se spillerens silo og lade |
| `/hayday admin give/take <spiller> <vare> <antal>` | Giv/fjern varer i lageret |
| `/hayday admin item <spiller> <mark\|bygning> [antal]` | Giv en mark/bygning som item |
| `/hayday admin xp/level/coins <spiller> <værdi>` | XP, level og penge |
| `/hayday admin skib <spiller> <ankom\|afsted>` | Styr skibet |
| `/hayday admin ordrer <spiller>` | Nye ordrer |
| `/hayday admin faerdigalle <spiller>` | Gør alle marker og bygninger færdige |
| `/hayday admin tp <spiller>` | Teleportér til spillerens gård |
| `/hayday admin oe <spiller> [info\|tp\|nulstil\|slet] [confirm]` | Spillerens ø – nulstil bygger øen forfra, slet frigiver pladsen |
| `/hayday admin fjernalt <spiller> confirm` | Fjern alle marker og bygninger |
| `/hayday admin reset <spiller> confirm` | Nulstil alt (data, gård, ø og vejbod) |
| `/hayday admin info · fjern · faerdig` | Mark/bygning du kigger på |
| `/hayday admin pakke [send]` | Resourcepack-status (og send igen) |
| `/hayday admin reload` | Genindlæs alle filer |

Admin-kommandoerne virker også på spillere der er offline – deres fil indlæses og gemmes automatisk.

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
`%hayday_orders_ready%` `%hayday_ship_state%` `%hayday_ship_time%` `%hayday_ship_filled%` `%hayday_rank%` `%hayday_top_name_<n>%` `%hayday_top_level_<n>%` `%hayday_top_xp_<n>%`
`%hayday_island_name%` `%hayday_island_visits%` `%hayday_island_likes%` `%hayday_island_access%` `%hayday_island_visitors%`

---

## ⚙️ Konfiguration

| Fil | Indhold |
|---|---|
| `config.yml` | Økonomi, øer, levels, marker, lager, ordrer, skib, vejbod, hologrammer, animationer, resourcepack, ItemsAdder og ikoner |
| `items.yml` | Alle varer og afgrøder (navn, ikon, salgspris, vækstid, vækststadier …) |
| `buildings.yml` | Bygninger, dyr og opskrifter |
| `messages.yml` | Alle beskeder |
| `holograms.yml` | Admin-hologrammer (gemmes automatisk) |

### 👤 Spillerfiler
Hver spiller har sin egen fil: `plugins/HayDay/players/<uuid>.yml`. Den indeholder navn, første login, sidst set,
level, XP, HayDay-mønter, silo og lade, ordrer, skibets kasser, vejbod-pladser og en oversigt over spillerens
marker, bygninger og ø (navn, adgang, venner, besøg og likes). Marker, bygninger, øer og vejbod-varer ligger desuden
samlet i `plugins/HayDay/data/`.
Hologrammer, dyr og animationer bruger ikke-persistente entities, så der aldrig ligger "døde" hologrammer tilbage i verdenen.

---

## 🛠️ Byg selv

```bash
mvn package
# -> target/HayDay-1.2.0.jar
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
├── island/                  Øerne: verdens-generator, stuehus, besøg, venner og beskyttelse
├── pack/                    Resourcepacken: bygning, hosting og font-tegn
├── listener/                Events og beskyttelse af marker/bygninger
├── manager/                 Gård, marked, ordrer, levels, animationer, lager …
├── model/                   Data-klasser
├── registry/                items.yml og buildings.yml
└── util/                    Hjælpere
tools/generate_pack.py       Tegner hele resourcepacken (menuer, items, ikoner, font)
```
