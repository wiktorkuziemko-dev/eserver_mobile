# E-SERVER PRO – aplikacja Android dla API E-SERVER

Aplikacja natywna (Kotlin + Jetpack Compose) pokazująca bieżący stan pojazdów na mapie
na podstawie API platformy E-SERVER (specyfikacja v2.0.0).

## Ekrany

| Ekran | Zawartość | Wykorzystane funkcje API |
|---|---|---|
| **Logowanie** | login, hasło (podgląd), „Zapamiętaj mnie”, opcjonalny adres serwera (np. środowisko testowe) | `devices` (weryfikacja danych) |
| **Mapa** | wszystkie pojazdy na mapie OpenStreetMap; kolor = stan, strzałka = kierunek jazdy, etykieta = nr rejestracyjny; licznik i filtr stanów; karta podglądu po kliknięciu pojazdu; auto-odświeżanie | `devices`, `positions` |
| **Pojazdy** | lista z wyszukiwarką (nr rej., nazwa, nr GPS), filtry stanów, prędkość, „ile minut temu”, pull-to-refresh | `devices`, `positions` |
| **Szczegóły pojazdu** | mini-mapa, bieżąca aktywność („Jazda od 10:26 · trwa 2 h 10 min”), prędkość, zapłon, kierunek, przebieg, czas pozycji, ostatni raport nadajnika, współrzędne (kopiowanie), podsumowanie dnia; przyciski: Historia tras / Na mapie / Dojazd / Udostępnij | `status`, `last_reading_date`, `history` |
| **Historia tras** | zakres: Dziś / Wczoraj / 7 dni / 30 dni / dowolny (kalendarz); podsumowanie (dystans, czas jazdy, czas postoju, liczba jazd); **tabela** jazd i postojów pogrupowana po dniach (od, do, czas, km); filtr „Tylko jazdy”; eksport CSV | `history` (`details=1`) |
| **Ustawienia** | konto, częstotliwość odświeżania (15 s – 2 min), legenda kolorów, wylogowanie | – |

Stany pojazdu: **Jazda** (zapłon wł., prędkość > 0) · **Zapłon wł.** (postój z włączonym silnikiem) · **Postój** (zapłon wył.) · **Brak danych**.

## Szczegóły techniczne

* Autoryzacja: `Authorization: Basic base64(login + ":" + md5(hasło))`, wszystkie zapytania metodą POST, parametry jako formularz.
* TLS 1.2+ wymuszony po stronie klienta; ruch nieszyfrowany zablokowany (`network_security_config`).
* Hasło nigdy nie jest zapisywane – przy „Zapamiętaj mnie” w `EncryptedSharedPreferences` (klucz w Android Keystore) trafia tylko login i skrót MD5.
* Daty wysyłane/odbierane w formacie `date_format=utc`, a aplikacja przelicza je na strefę czasową telefonu – niezależnie od strefy ustawionej na koncie.
* Parser odpowiedzi jest tolerancyjny (np. `last_reading_date` w obu formach: z dokumentacji i z przykładu Postmana; daty jako tekst lub timestamp).
* Mapa: osmdroid + kafelki OpenStreetMap – nie wymaga klucza Google Maps API. W trybie ciemnym kafelki są odwracane kolorystycznie.
* Odświeżanie pozycji działa tylko, gdy aplikacja jest na pierwszym planie.
* minSdk 26 (Android 8.0), targetSdk 35.

```
app/src/main/java/pl/fleet/monitor/
├── data/            klient API, modele, sesja (warstwa bez zależności od UI)
├── ui/login/        ekran logowania
├── ui/map/          mapa, rysowanie znaczników
├── ui/list/         lista pojazdów
├── ui/details/      szczegóły pojazdu
├── ui/history/      historia tras (tabela)
└── MainActivity.kt  nawigacja
```

## Budowanie

**Android Studio** (Ladybug lub nowsze): *File → Open* → wskaż katalog projektu → *Run*.

**Linia poleceń** (wymagany JDK 17 i Android SDK):

```bash
./gradlew testDebugUnitTest   # testy klienta API
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

**GitHub Actions**: po wypchnięciu repozytorium workflow `.github/workflows/build.yml`
uruchamia testy i buduje APK (debug + release), do pobrania w zakładce *Actions → Artifacts*.

> Wersja `release` jest podpisana kluczem debug – przed publikacją w Google Play skonfiguruj własny keystore
> i zmień `applicationId` (`pl.fleet.monitor`) na docelowy.
