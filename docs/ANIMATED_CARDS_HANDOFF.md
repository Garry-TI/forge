# Engineering Handoff: Forge MTG Animated Cards Implementation & Multi-Art Scoping Fix

**Target Audience:** Autonomous Coding Agents (Claude, OpenAI Codex) & Forge Contributors  
**Repository:** `https://github.com/Garry-TI/forge.git`  
**Upstream:** `https://github.com/Card-Forge/forge.git`  
**Active Branch:** `feature/animated-cards`  
**Target Environment:** Windows 11 (PowerShell / Git Bash), Java 23 / 21 / 17, Maven 3.9+, Python 3.10+, ffmpeg  
**Date:** September 2026  

---

## 1. Executive Summary & Problem Statement

### 1.1 Objective
The `feature/animated-cards` branch implements support for looping animated card art across:
1. **Forge Desktop (Swing UI):** Hand, Battlefield, Stack, Deck Editor, and Card Zoom / Picture zoom panels.
2. **Forge Mobile & Adventure (LibGDX UI):** In-game card rendering, card inspection, and battlefield rendering.
3. **Generation Tooling:** Python CLI (`scripts/generate_card_animation.py`) and Windows / Bash launchers (`generate-card-animation.cmd`, `generate-card-animation.sh`) that extract video frames with `ffmpeg`, composite them onto standard 488x680 card borders, and deploy them to Forge's resource directories.

### 1.2 User-Reported Bugs & Evidence
The user reported two critical defects with screenshot evidence (`C:\Users\Garry\Pictures\2026-09-23_16-24.png`):
1. **Wrong Animation on Card:** The animated art of the card *Improvised Weaponry* (a dwarf warrior swinging a glowing gold mace at a mind flayer) was rendered inside the art box of *Jumbo Cactuar* (a Final Fantasy green cactus creature).
2. **Animation Leaked to All Art Variants:** The animated art was applied to **both** printings of *Jumbo Cactuar* in the `FIN` set:
   * Collector `#191` (Standard frame by Jason Kiantoro)
   * Collector `#343` (Alternate art variant by Hisashi Momose)
   Only collector `#191` was intended to have an animation, but both displayed the animation.

---

## 2. Root Cause Analysis

### 2.1 Bug 1: Why Improvised Weaponry Art Ended Up on Jumbo Cactuar
During testing, the command executed was:
```bash
./generate-card-animation.cmd FIN "Jumbo Cactuar" "minimax-h3_animate-this-scene.mp4" --number 191
```
The file `minimax-h3_animate-this-scene.mp4` was the test MP4 clip previously generated for *Improvised Weaponry*. The generator accepted this video file without verifying video metadata against the card art, extracted its frames, composited them onto the template for *Jumbo Cactuar* #191, and deployed them.

**Fix Required:**
* The generation script must validate that the user is passing an animation clip corresponding to the specified card, or print a clear confirmation displaying the video filename, target set, card name, and collector number before proceeding.
* Clean up any incorrect frame files currently deployed under `res/animated_cards/` for `Jumbo Cactuar`.

### 2.2 Bug 2: Why the Animation Leaked to All Art Variants (The Scoping Flaw)
This is an architectural flaw in how card animations are registered, queried, and resolved.

#### A. Overly Broad Fuzzy Prefix Matching in `CardAnimationManager`
In `CardAnimationManager.java`:
```java
// forge-gui-desktop/src/main/java/forge/gui/CardAnimationManager.java (Line 255)
for (String k : INSTANCE.cardAnimations.keySet()) {
    if (k.startsWith(key + "_") || k.startsWith(key + "1")
            || k.endsWith("/" + key) || k.endsWith("/" + key + "1")
            || k.endsWith("_" + key) || k.endsWith("_" + key + "1")) {
        return true;
    }
}
```
When `hasAnimation("Jumbo Cactuar")` is evaluated:
Because the animation for variant #191 was deployed under keys like `jumbo cactuar_191` and `jumbo cactuar1`, `k.startsWith(key + "_")` evaluated to `true`.
Consequently, **any query using the base card name returns `true`**, and `getCurrentFrame("Jumbo Cactuar")` returns frame variant 1 for any card sharing that name!

#### B. Incomplete Object Inspection in `CardPicturePanel.java`
In `CardPicturePanel.java`:
```java
private int getDisplayedCardArtIndex() {
    if (displayed instanceof CardStateView) {
        CardView cv = ((CardStateView) displayed).getCard();
        if (cv != null) {
            IPaperCard pc = cv.getPaperCard();
            if (pc != null) return pc.getArtIndex();
        }
    } else if (displayed instanceof PaperCard) {
        return ((PaperCard) displayed).getArtIndex();
    }
    return 0; // BUG: Defaulted to 0 for raw CardView, CardRules, or InventoryItem
}
```
When cards are previewed from certain UI components (e.g., catalog lists or deck editors), `displayed` can be a raw `CardView` or an `InventoryItem`. `getDisplayedCardArtIndex()` returned `0`.
In `CardAnimationManager`:
```java
int art = artIndex > 0 ? artIndex : 1; // 0 was coerced to 1!
```
And because `artIndex > 1` was false (since `artIndex` was `0`), the method fell back to `hasAnimation(cardName)`, which returned variant 1!

#### C. Incomplete Scoping in Mobile UI (`CardRenderer.java` & `CardImageRenderer.java`)
In `CardRenderer.java`:
```java
// Line 640
if (pc != null && CardAnimationManager.hasAnimation(pc.getName())) {
    image = CardAnimationManager.getCurrentFrame(pc.getName());
}
```
`CardRenderer` had access to `pc` (`IPaperCard`), but discarded its edition, collector number, and art index, querying `CardAnimationManager` using only `pc.getName()`.

---

## 3. Forge's Native Image Resolution Architecture (The Golden Standard)

Forge already has a battle-tested system for resolving unique card images across sets, printings, and alternate arts. **The animation engine must adhere strictly to Forge's native image key architecture instead of inventing custom fuzzy string matching.**

### 3.1 How Forge Identifies Card Art: `ImageUtil.java`
Inspect `forge-core/src/main/java/forge/util/ImageUtil.java` (lines 101–155):
```java
public static String getImageRelativePath(IPaperCard cp, String face, boolean includeSet, boolean isDownloadUrl)
```
1. **Art Count Query:**
   `cntPictures = db.getArtCount(card.getName(), edition, cp.getFunctionalVariant());`
2. **Key Generation:**
   * If a card has **only 1 art** in that set:
     `imageKey = "<SET>/<CardName>.full"` (e.g., `AFR/Improvised Weaponry.full`)
   * If a card has **multiple arts** in that set:
     `imageKey = "<SET>/<CardName><ArtIndex>.full"`:
     * Variant 1: `FIN/Jumbo Cactuar1.full`
     * Variant 2: `FIN/Jumbo Cactuar2.full`
3. **Canonical Keys on Domain Objects:**
   * `IPaperCard`: `pc.getImageKey(false)` -> returns exact `imageKey` (e.g. `FIN/Jumbo Cactuar1.full`).
   * `CardView` / `CardStateView`: `card.getCurrentState().getImageKey()` -> returns exact `imageKey`.
   * `CardRules`: Single art cards have standard names; multi-art sets define art counts in `res/editions/<Edition>.txt`.

---

## 4. Required Implementation Specifications

### 4.1 Strict Keying in `CardAnimationManager` (Desktop & Mobile)
Replace loose fuzzy string matching with exact `imageKey` lookup.

#### Data Structure:
```java
// Map canonical normalized image key -> frame array
// Examples of keys:
//   "fin/jumbo cactuar1.full"
//   "fin/jumbo cactuar_191"
//   "afr/improvised weaponry.full"
private final Map<String, BufferedImage[]> cardAnimations = new ConcurrentHashMap<>();
```

#### Lookup Hierarchy for `hasAnimation`:
When a card component queries for an animation, pass the card's native `imageKey` (or `IPaperCard` / `CardStateView` directly):
```java
public static boolean hasAnimation(IPaperCard pc) {
    if (pc == null) return false;
    return hasAnimation(pc.getImageKey(false), pc.getEdition(), pc.getName(), pc.getArtIndex(), pc.getCollectorNumber());
}

public static boolean hasAnimation(CardView cv) {
    if (cv == null) return false;
    CardStateView state = cv.getCurrentState();
    String key = state != null ? state.getImageKey() : null;
    IPaperCard pc = cv.getPaperCard();
    if (pc != null) return hasAnimation(pc);
    return hasAnimation(key);
}

public static boolean hasAnimation(String imageKey, String edition, String cardName, int artIndex, String collectorNumber)
```

#### Matching Logic:
1. **Exact ImageKey Match:**
   Normalize `imageKey` (lowercase, remove `.full`, `.jpg`, special characters):
   If `imageKey == "FIN/Jumbo Cactuar1.full"`, check normalized `fin/jumbocactuar1`. If found, **match**.
2. **Collector Number Match:**
   If `edition` and `collectorNumber` are present, check `<edition>/<collectorNumber>` and `<edition>/<cardName>_<collectorNumber>`. If found, **match**.
3. **Art Index Match:**
   If `edition`, `cardName`, and `artIndex > 0` are present, check `<edition>/<cardName><artIndex>`. If found, **match**.
4. **Fallback to Bare Card Name (`cardName`):**
   > [!CRITICAL]
   > **DO NOT** fall back to bare `cardName` if the card has multiple arts in the edition, or if `artIndex > 1`!
   > Only allow fallback to bare `cardName` if:
   > `(artIndex <= 1) && (db.getArtCount(cardName, edition) <= 1)`
   > If the card has multiple arts and this is variant 2, 3, etc., returning `true` for a bare card name is a fatal bug.

### 4.2 UI Call Site Updates

#### Desktop: `CardPicturePanel.java`
Inspect `forge-gui-desktop/src/main/java/forge/gui/CardPicturePanel.java`:
Update `setImage(final Object display, ...)`:
Extract the canonical `imageKey` directly:
```java
private String getDisplayedImageKey() {
    if (displayed instanceof CardStateView) {
        return ((CardStateView) displayed).getImageKey();
    } else if (displayed instanceof CardView) {
        CardStateView state = ((CardView) displayed).getCurrentState();
        return state != null ? state.getImageKey() : null;
    } else if (displayed instanceof IPaperCard) {
        return ((IPaperCard) displayed).getImageKey(isFlipped);
    } else if (displayed instanceof InventoryItem) {
        return ((InventoryItem) displayed).getImageKey(isFlipped);
    }
    return null;
}
```
Query `CardAnimationManager` using `getDisplayedImageKey()` along with `IPaperCard` details.

#### Desktop: `CardPanel.java`
Inspect `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java`:
Update `setImage(...)`:
```java
String imageKey = null;
if (card != null) {
    if (card.getCurrentState() != null) {
        imageKey = card.getCurrentState().getImageKey();
    }
    if (imageKey == null && card.getPaperCard() != null) {
        imageKey = card.getPaperCard().getImageKey(false);
    }
}
```
Query `CardAnimationManager.hasAnimation(imageKey, ...)` instead of un-scoped card name.

#### Mobile: `CardRenderer.java` & `CardImageRenderer.java`
Inspect `forge-gui-mobile/src/forge/card/CardRenderer.java`:
In `drawCard(Graphics g, IPaperCard pc, ...)`:
```java
// Pass pc or pc.getImageKey(false)
String imgKey = pc != null ? pc.getImageKey(false) : null;
if (imgKey != null && CardAnimationManager.hasAnimation(imgKey, pc.getEdition(), pc.getName(), pc.getArtIndex(), pc.getCollectorNumber())) {
    image = CardAnimationManager.getCurrentFrame(imgKey);
}
```

### 4.3 Python Generator Updates (`scripts/generate_card_animation.py`)

1. **Deployment Paths:**
   When `--number` / `-n` is supplied:
   * Resolve `art_index` and `art_count` from `res/editions/<Edition>.txt`.
   * Save frames into:
     * `res/animated_cards/<SET>/<CardName><ArtIndex>.full/`
     * `res/animated_cards/<SET>/<CollectorNumber>/`
     * `res/animated_cards/<SET>/<CardName>_<CollectorNumber>/`
   * **Do NOT** write un-indexed frames into `res/animated_cards/<CardName>/` if `art_count > 1`!
2. **Video Validation & Sanity Check:**
   * Print a pre-flight summary:
     ```text
     Target Set:        FIN
     Card Name:         Jumbo Cactuar
     Collector Number:  191 (Art 1 of 2)
     Input Video File:  <video_path>
     Video Duration:    5.16s (124 frames @ 24fps)
     ```
   * Prompt user or accept `--yes` / `-y` flag if running interactively.

---

## 5. File Inventory & Current State

| File Path | Component | Status & Tasks |
|:---|:---|:---|
| `forge-gui-desktop/src/main/java/forge/gui/CardAnimationManager.java` | Desktop Animation Manager | **Needs Update:** Replace loose fuzzy `startsWith` logic with exact `imageKey` & scoped matching. Add `hasAnimation(IPaperCard)`, `hasAnimation(CardView)`. |
| `forge-gui-desktop/src/main/java/forge/gui/CardPicturePanel.java` | Desktop Card Zoom/Preview | **Needs Update:** Extract `imageKey` from all `displayed` instances (`CardStateView`, `CardView`, `IPaperCard`, `InventoryItem`). |
| `forge-gui-desktop/src/main/java/forge/view/arcane/CardPanel.java` | Desktop In-Game Card Panel | **Needs Update:** Query using `card.getCurrentState().getImageKey()`. |
| `forge-gui-desktop/src/main/java/forge/toolbox/special/CardZoomer.java` | Desktop Card Zoom Dialog | **Needs Update:** Query using `card.getCurrentState().getImageKey()`. |
| `forge-gui-mobile/src/forge/card/CardAnimationManager.java` | Mobile LibGDX Animation Manager | **Needs Update:** Mirror Desktop's strict `imageKey` matching. |
| `forge-gui-mobile/src/forge/card/CardRenderer.java` | Mobile Card Renderer | **Needs Update:** Pass `pc.getImageKey(false)` instead of `pc.getName()`. |
| `forge-gui-mobile/src/forge/card/CardImageRenderer.java` | Mobile Image Renderer | **Needs Update:** Pass `card.getCurrentState().getImageKey()`. |
| `scripts/generate_card_animation.py` | Python Extraction & Compositor | **Needs Update:** Deploy frames matching canonical Forge `imageKey` paths; prevent bare folder creation for multi-art cards. |
| `generate-card-animation.cmd` | Windows Launcher | **Fixed in `0c480aa973d`:** Uses `goto :show_usage` to avoid CMD parenthesis parsing crash. |
| `generate-card-animation.sh` | Linux/macOS Launcher | **Fixed in `0c480aa973d`:** Added script directory fallbacks. |

---

## 6. Build, Deployment, and Verification Protocol

### Step 1: Recompile Binaries
```bash
mvn package -pl forge-gui-desktop,forge-gui-mobile-dev -am -DskipTests
```

### Step 2: Deploy to Forge Snapshot Runtime
```powershell
# Copy Desktop launcher binaries
Copy-Item "forge-gui-desktop\target\forge-gui-desktop-2.0.15-SNAPSHOT-jar-with-dependencies.jar" -Destination "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\forge-gui-desktop-2.0.15-SNAPSHOT-jar-with-dependencies.jar" -Force
Copy-Item "forge-gui-desktop\target\forge.exe" -Destination "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\forge.exe" -Force

# Copy Mobile/Adventure binaries
Copy-Item "forge-gui-mobile-dev\target\forge-gui-mobile-dev-2.0.15-SNAPSHOT-jar-with-dependencies.jar" -Destination "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\forge-gui-mobile-dev-2.0.15-SNAPSHOT-jar-with-dependencies.jar" -Force
Copy-Item "forge-gui-mobile-dev\target\forge-adventure.exe" -Destination "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\forge-adventure.exe" -Force
```

### Step 3: Clean Stale Frames
Remove any invalid composite frames generated with the wrong video file:
```powershell
Remove-Item -Path "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\res\animated_cards\*Jumbo Cactuar*" -Recurse -Force
Remove-Item -Path "forge-installer\target\forge-installer-2.0.15-SNAPSHOT\res\animated_cards\FIN" -Recurse -Force
Remove-Item -Path "res\animated_cards\*Jumbo Cactuar*" -Recurse -Force
Remove-Item -Path "res\animated_cards\FIN" -Recurse -Force
```

### Step 4: Verification Test Matrix
Write and run a Java verification harness (e.g. `TestScoping.java`):
1. `hasAnimation("FIN/Jumbo Cactuar1.full")` -> MUST return `true` (if art 1 animation deployed).
2. `hasAnimation("FIN/Jumbo Cactuar2.full")` -> MUST return `false` (no animation for art 2).
3. `hasAnimation("AFR/Improvised Weaponry.full")` -> MUST return `true`.
4. `hasAnimation("Jumbo Cactuar")` -> MUST return `false` (or only match if explicitly scoped, preventing leakage to other printings).
5. In Forge Desktop UI: Open Deck Editor -> Search "Jumbo Cactuar":
   * Click variant `#191`: Animation MUST play.
   * Click variant `#343`: Static card art MUST render (no animation).
