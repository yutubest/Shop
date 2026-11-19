# Slimo Mūzika 🎵

Personīgs mūzikas atskaņotājs **bez reklāmām**. Darbojas kā aplikācija telefonā (PWA).

## Ko tā prot
- 🔍 Meklēt jebkuru dziesmu vai video (cilnes “Dziesmas” / “Video”)
- ▶ ⏸ ⏮ ⏭ Atskaņot, pauzēt, nākamā / iepriekšējā, pārtīt
- 🔀 Jaukt un 🔁 atkārtot (visu / vienu)
- 📱 **Skan ar izslēgtu ekrānu** — pogas redzamas bloķēšanas ekrānā un strādā ar austiņu pogām
- ❤️ “Patīk” saraksts + 📃 savi saraksti (saglabājas telefonā)
- 🎬 Var pārslēgt uz video skatu
- ⬇⬆ Eksportēt / importēt savus sarakstus (rezerves kopija)

## Kā dabūt telefonā
Aplikācija ir tikai statiski faili (`index.html`, `app.js`, …), tos var ielikt jebkurā hostingā, piem.:
- **GitHub Pages**, **Netlify Drop** (velc mapi `music-player` uz app.netlify.com/drop) vai **Cloudflare Pages**.

Tad atver adresi telefonā (Chrome uz Android / Safari uz iPhone) un izvēlnē spied
**“Install app” / “Pievienot sākuma ekrānam”**. Ieslēdz dziesmu, izslēdz ekrānu — mūzika turpina skanēt.

## No kurienes nāk mūzika
Aplikācija ņem dziesmas no YouTube caur bezmaksas atvērtā koda serveriem
(**Piped** un **Invidious**), kuri nerāda reklāmas. Ja viens nestrādā,
automātiski tiek mēģināts nākamais. Sarakstu var mainīt **Iestatījumos**.

Publiskie serveri dažreiz ir pārslogoti. **Visdrošāk** ir palaist savu serveri uz datora
(tas arī uzreiz pasniedz pašu aplikāciju):

```bash
pip install -U yt-dlp
python music-player/server.py
```

Tad telefonā (tajā pašā Wi-Fi) atver `http://<datora-IP>:8000` — aplikācija
pati izmantos tavu serveri. Vai ieraksti šo adresi Iestatījumos laukā “Savs serveris”.

> Video režīmā telefons var apturēt atskaņošanu, kad ekrāns izslēgts —
> fona klausīšanās notiek audio režīmā (noklusējums).
