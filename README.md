# Toyota Clima

Aplicație Android care pornește climatizarea Toyota la ore programate, în funcție de vremea de afară:

- sub pragul de încălzire (implicit 12°C) → încălzire la 22°C, iar sub 3°C și dezaburire parbriz/lunetă;
- peste pragul de răcire (implicit 24°C) → răcire la 20°C;
- între praguri → nu pornește nimic.

Implicit: luni–vineri la 06:20 și 14:30. Orele, zilele, pragurile și locația se schimbă din aplicație.

## Instalare

1. Descarcă `toyota-clima.apk` din [Releases → latest](../../releases/tag/latest).
2. Deschide-l pe telefon și permite instalarea din surse necunoscute.
3. În **Cont MyToyota și locație** completează emailul și parola. Totul se salvează automat.
4. Dacă apare avertismentul portocaliu, apasă **Permite** și **Dezactivează** (alarme exacte, optimizare baterie).
5. Apasă **Testează**: verifică vremea și contul Toyota, fără să pornească mașina.
6. Pornește comutatorul din panoul de sus.

## Calendarul

Zilele colorate sunt cele în care pornește clima. Programul de bază vine din zilele săptămânii
(implicit luni–vineri). O atingere pe o zi din calendar o pornește sau o oprește doar pe ea,
de exemplu un concediu sau o sâmbătă lucrătoare; punctul marchează aceste excepții.

Orele de pornire se schimbă atingându-le; ținute apăsat se șterg.
Fiecare pornire programată trimite o notificare cu rezultatul și apare în jurnal.

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
