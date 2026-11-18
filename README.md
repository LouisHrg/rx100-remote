# RX100 Remote

Application Android open source (Kotlin + Jetpack Compose) qui affiche le live view d'un **Sony RX100 III** en Wi-Fi sur le téléphone, avec une télécommande minimale (déclencheur, zoom, miroir).

Elle utilise la **Sony Camera Remote API** (HTTP + JSON-RPC), implémentée à partir de la spécification publique — sans le code du SDK sample de Sony. Pas de pub, pas de tracking, aucune connexion en dehors du Wi-Fi de l'appareil.

## Connecter l'appareil

1. Sur le RX100 III : `MENU` › onglet **Appli** › **Liste des applications** › **Contrôle par smartphone** (« Smart Remote Control »). Si l'appli n'est pas là, mets à jour le firmware de l'appareil ou installe-la depuis PlayMemories Camera Apps.
2. L'appareil affiche un SSID `DIRECT-xxxx:DSC-RX100M3` et un mot de passe.
3. Sur le téléphone : Paramètres › Wi-Fi › connecte-toi à ce réseau. Quand Android/ColorOS signale « pas d'accès à internet », choisis **Rester connecté** (sur ColorOS, désactive aussi « Changement intelligent de réseau » / « Wi-Fi+ » si le téléphone repasse sur les données mobiles).
4. Lance **RX100 Remote** et accepte la permission « Appareils à proximité ». L'appli :
   - lie son process au réseau Wi-Fi (requête réseau Wi-Fi *sans* internet + `bindProcessToNetwork`) ;
   - cherche l'appareil en SSDP (`urn:schemas-sony-com:service:ScalarWebAPI:1`) ;
   - à défaut, utilise l'URL de repli (`http://192.168.122.1:8080/sony`, modifiable à l'écran) ;
   - appelle `getAvailableApiList`, `startRecMode` si annoncé, `setShootMode still` si annoncé, puis `startLiveview`.

Dans le live view : **●** déclenche, **Zoom + / −** apparaissent si `actZoom` est annoncé, **Miroir** inverse l'image horizontalement, **Stats** affiche FPS / temps de décodage / âge de l'image / nombre de resynchronisations, **Logs** montre toutes les requêtes et réponses JSON brutes (bouton **Copier** pour me les renvoyer).

## Installer sans Android Studio

Chaque push sur GitHub lance le workflow `.github/workflows/build.yml`, qui exécute les tests et compile l'APK debug. Dans l'onglet **Actions** du dépôt, ouvre le dernier run et télécharge l'artefact `rx100-remote-debug-apk` (zip contenant `app-debug.apk`). Copie-le sur le téléphone et installe-le (autoriser l'installation d'applis inconnues pour le gestionnaire de fichiers).

Avec `adb` et le téléphone en débogage USB :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Compiler localement

Prérequis : JDK 17 et le SDK Android (platform `android-37.0`). Sur macOS avec Homebrew :

```bash
brew install openjdk@17 && brew install --cask android-commandlinetools
```

```bash
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
```

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleDebug
```

L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.

## Structure

- `protocol/` — Kotlin pur, sans dépendance Android, testé : `Ssdp` (M-SEARCH + XML de description), `CameraApi` (JSON-RPC), `LiveviewParser` (flux live view Sony, avec resynchronisation).
- `net/CameraNetwork` — liaison au réseau Wi-Fi de l'appareil, MulticastLock.
- `ui/` — `CameraViewModel` (StateFlow, session, reconnexion, décodage) et écrans Compose.

Pour ajouter une fonction (ISO, vitesse, EV, vidéo, touch AF…), il suffit d'appeler `CameraApi.call("setIsoSpeedRate", …)` depuis le ViewModel après avoir vérifié la liste `state.apis`.

## Limites connues

- Non testé sur un vrai RX100 III au moment de l'écriture (voir la checklist ci-dessous).
- HTTP en clair autorisé uniquement vers `192.168.122.1` et `10.0.0.1` (`res/xml/network_security_config.xml`). Une URL manuelle vers une autre IP sera refusée : ajoute l'IP dans ce fichier.
- Si le téléphone est connecté à un autre Wi-Fi (box), l'appli s'y liera : l'écran de connexion affiche l'IP et la passerelle pour vérifier (`192.168.122.x` = appareil).
- Zoom par crans (`actZoom` `1shot`), pas de zoom continu.
- Pas de `getEvent` : la liste des API est rafraîchie après chaque action, pas en continu.
- La « latence » affichée est mesurée côté téléphone (réception → affichage) ; la latence capteur → téléphone n'est pas mesurable sans horloge commune.
- Pas de réglages (ISO, vitesse, ouverture, EV), vidéo, touch focus, grille ni transfert de photos pour l'instant.

## Checklist de test sur l'appareil

Après chaque point, en cas de souci : **Logs › Copier** et colle le résultat dans une issue.

1. Lancement : la demande de permission apparaît, l'écran de connexion s'affiche.
2. Wi-Fi : « Wi-Fi lié — IP 192.168.122.x, passerelle 192.168.122.1 ». Les données mobiles activées, le téléphone reste-t-il sur le Wi-Fi de l'appareil ?
3. SSDP : les logs montrent une réponse `SSDP <-` et une `Description XML`. Sinon, l'URL de repli doit marcher.
4. API : la liste contient `startRecMode` puis, après lui, `startLiveview`, `actTakePicture`, `actZoom`.
5. Live view : image fluide, compteur `resync` à 0 ou presque (Stats). Noter le FPS.
6. Déclencheur : toast « Photo prise », photo bien enregistrée sur la carte.
7. Zoom + / − : l'objectif bouge.
8. Rotation portrait/paysage : le live view continue sans reconnexion.
9. Reconnexion : éteindre puis rallumer le Wi-Fi de l'appareil (ou s'éloigner) — message « Flux coupé, reconnexion… », puis reprise ou message d'erreur clair.
10. Écran reste allumé pendant le live view ; barres système masquées (balayer pour les afficher).

## Licence

GPL-3.0 : une télécommande libre doit le rester, les forks doivent publier leurs modifications.
