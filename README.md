# Toyota Clima

Aplicație Android care pornește climatizarea Toyota la ore programate, în funcție de vremea de afară:

- sub pragul de încălzire (implicit 12°C) → încălzire la 22°C, iar sub 3°C și dezaburire parbriz/lunetă;
- peste pragul de răcire (implicit 24°C) → răcire la 20°C;
- între praguri → nu pornește nimic.

Implicit: luni–vineri la 06:20 și 14:30. Orele, zilele, pragurile și locația se schimbă din aplicație.

## Instalare

1. Descarcă `toyota-clima.apk` din [Releases → latest](../../releases/tag/latest).
2. Deschide-l pe telefon și permite instalarea din surse necunoscute.
3. În aplicație: completează emailul și parola MyToyota, apasă **Salvează și programează**.
4. Apasă **Permite alarme exacte** și **Scoate din optimizarea bateriei**.
5. Apasă **Test**: verifică vremea și login-ul Toyota, fără să trimită comanda.
6. Cu mașina încuiată, apasă **Pornește climatizarea acum** și verifică în MyToyota.
7. Bifează **Programare activă** și salvează.

Fiecare rulare programată trimite o notificare cu rezultatul și apare în jurnalul din aplicație.

## Mașina: geamuri și uși

Secțiunea **Mașina** din aplicație:

- **Verifică starea**: trezește mașina și arată ce geamuri sunt deschise și ce uși sunt descuiate (durează până la ~40 s).
- **Închide geamurile**, **Încuie ușile**, **Descuie ușile** (cere confirmare): după comandă, aplicația așteaptă ca mașina să raporteze schimbarea și spune dacă s-a confirmat.
- **Găsește mașina**: pornește avariile scurt.

Pe serverele Toyota Europe, deschiderea geamurilor de la distanță nu e acceptată, doar închiderea.
Fiecare verificare trezește modemul mașinii și consumă puțin din bateria de 12V, deci nu o apăsa în buclă.

## Cerințe și limitări

- Mașina trebuie să aibă climatizare la distanță în MyToyota (Remote Services activ).
- Contul MyToyota fără autentificare în doi pași.
- API-ul Toyota e neoficial, portat din [pytoyoda](https://github.com/pytoyoda/pytoyoda). Toyota îl poate schimba oricând.
- Parola se ține în stocarea privată a aplicației, pe telefon. Nu pleacă nicăieri în afară de serverele Toyota.
- Comanda e respinsă dacă mașina e descuiată, o ușă e deschisă, cheia e înăuntru sau climatizarea e deja pornită.

## Build

Fiecare push pe `main` compilează APK-ul cu GitHub Actions și îl publică în release-ul `latest`.
Proiectul nu are dependențe externe (doar Android SDK + Kotlin stdlib).

`tools/toyota_clima.py` e varianta în Python a aceleiași logici, utilă pentru testare din calculator.
