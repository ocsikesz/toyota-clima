# Toyota Clima

Aplicație Android care pornește climatizarea Toyota la ore programate, în funcție de vremea de afară:

- sub pragul de încălzire (implicit 12°C) → încălzire la 22°C, iar sub 3°C și degivrare parbriz și lunetă;
- peste pragul de răcire (implicit 24°C) → răcire la 20°C;
- între praguri → nu pornește nimic.

Implicit: luni–vineri la 06:20 și 14:30. Orele, zilele, pragurile și locația se schimbă din aplicație.

## Instalare

1. Descarcă `toyota-clima.apk` din [Releases → latest](../../releases/tag/latest).
2. Deschide-l pe telefon și permite instalarea din surse necunoscute.
3. În **Setări → Cont Toyota** completează emailul și parola. Totul se salvează automat.
4. Dacă pe **Acasă** apare avertismentul portocaliu, apasă **Permite** și **Dezactivează** (alarme exacte, optimizare baterie).
5. În **Setări → Test** apasă **Testează**: verifică vremea și contul Toyota, fără să pornească mașina.
6. Pornește comutatorul din panoul albastru de pe **Acasă**.

## Paginile

- **Acasă**: următoarea pornire și calendarul lunii. Discurile pline sunt zilele din program, cele cu contur au un
  eveniment din calendar. O atingere pe o zi o pornește sau o oprește doar pe ea; punctul marchează excepțiile.
- **Setări**: contul Toyota, programul săptămânal și orele, temperaturile, evenimentele din calendar și testul.
- **Jurnal**: fiecare pornire și fiecare test.

## Evenimente din Google Calendar

Activează **Setări → Evenimente din calendar** și permite accesul la calendar. Orice eveniment care are în titlu
cuvântul ales (implicit „mașina”, fără să conteze diacriticele sau literele mari) pornește clima cu 5 minute
înainte de ora lui, după aceleași reguli de temperatură. Evenimentele de o zi întreagă sunt ignorate, iar
cele aflate la mai puțin de 15 minute de o pornire din program nu pornesc clima a doua oară. Aplicația
reverifică calendarul la fiecare jumătate de oră, deci un eveniment adăugat de pe alt dispozitiv e preluat după
ce se sincronizează pe telefon.

Waze nu permite altor aplicații să-i citească drumurile planificate, dar folosește și el Google Calendar.

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
