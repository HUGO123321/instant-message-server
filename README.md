# Úkol 4 - Instant Message Sever

[![UTB FAI Task](https://img.shields.io/badge/UTB_FAI-Task-yellow)](https://www.fai.utb.cz/)
[![Java](https://img.shields.io/badge/Java-007396.svg?logo=java&logoColor=white)](https://www.java.com/)
[![Gradle](https://img.shields.io/badge/Gradle-02303A.svg?logo=gradle&logoColor=white)](https://gradle.org/)

|                                  |                                                   |
| -------------------------------- | ------------------------------------------------- |
| __Maximální počet bodů__         | 11 bodů                                           |
| __Způsob komunikace s aplikací__ | Parametry předané při spuštění + síťové připojení |
| __Způsob testování aplikace__    | Na základě odesílaných zpráv po síti              |

---

## 📝 Zadání úkolu 

Cílem této úlohy bude implementovat instant message server. Princip je v zásadě podobný jako u předchozí úlohy při implementaci jednoduchého echo serveru. Pouze budou přidány další funkcionality, které budou popsány dále v zadání této úlohy. __Jako klienta můžete použít telnet klienta, kterého jste implementovali již v jedné z předchozích úloh.__

### Požadavky:
* __"Identifikace uživatelů"__ - Uživatel bude schopen zasláním speciálního řetězce ```#setMyName <jméno>``` změnit své jméno, které se zobrazuje ostatním klientům. Uživatel bude moct ostatním posílat zprávy až ve chvíli, kdy bude mít nastavené jméno. Jméno musí být bez mezer! __Hned po připojení klienta na IM server bude vyzván k zadání svého jména a to bez nutnosti psaní příkazu #setMyName.__
* __Možnost zasílání soukromých zpráv__ - Možnost zaslání soukromé zprávy pomocí speciálního řetězce ```#sendPrivate <jméno> <zpráva>```
* __Možnost připojit se do diskuzní místnosti__ - Uživatel bude mít možnost připojit se do libovolné diskuzní místnosti. To provede tím, že zadá následující příkaz ```#join <název>```. Pokud taková místnost ještě na serveru neexistuje, tak vytvoří novou. Ve chvíli kdy uživatel píše zprávy tak je vidí jen ti uživatelé, kteří jsou ve stejné skupině jak uživatel, který je odesílá. Při připojení nového uživatele na server bude automaticky zařazen do diskuzní místnosti s názvem "__public__".
* __Možnost odpojit se z diskuzní místnosti__ - Uživatel bude mít také možnost z místnosti odejít. To provede následujícím příkazem ```#leave <název>```. 

__Základní implementace již funkčního IM serveru naleznete v tomto repozitáři. Vaším úkolem bude tedy jen jeho rozšíření o výše zmíněné funkcionality.__

---

### Popis základní implementace IM serveru
Jedná se o jednoduchý server, který zprávu = řetězec zaslaný po síti v kódování UTF-8 od klienta rozesílá všem ostatním klientům.

O každého klienta se stará jedna instance třídy __SocketHandler__, v níž jsou definovány 2 vnitřní třídy, tasky pro ThreadPool OutputHandler a InputHandler. Oba tyto tasky běží paralelně, jeden se stará o příchozí data ze socketu klienta, druhý o odchozí data.

Jedná se o komunikaci klient1-server-klient2 a ta funguje tak, že klient1 pošle data, jeho InputHandler je přijme a vloží je do všech front __(proměnná "messages")__ všech aktivních SocketHandlerů.

OutputHandler klienta2 čeká na data, která přijdou do jeho fronty zpráv. Jakmile přijdou, probudí se, data z fronty vezme a pošle je svému klientovi.

Všichni právě připojení klienti (resp. jejich SocketHandler-s) jsou uchovávání v množině __activeHandlers__.

### 💡 Tip
> Pokud bychom chtěli přidat funkci pro doručování zpráv jen vybraným klientům, bylo by lepší místo množiny HashSet použít __ConcurrentHashMap<String, SocketHandler>__, v níž bychom jako klíč používali řetězec __clientID__. Zprávy, které mají jít jen jednomu klientovi, by pak mohly začínat řetězcem clientID - pokud by server na začátku zprávy našel clientID, našel by si v HashMapě podle něj referenci na SocketHandler, který tohoto klienta obsluhuje, a zprávu by přidal do fronty pouze jemu.

--- 

### Struktura pole vstupních parametrů
1. __args[0]__ - Port, na kterém bude server naslouchat _(int)_
2. __args[1]__ - Maximální počet klientů, kteří se mohou k serveru v jeden okamžik připojit.  _(int)_

### Seznam všech příkazů

| Příkaz                          | Popis                                                                                                                                                                                  |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `#setMyName <jméno>`            | Změní jméno uživatele na zadané `<jméno>`. Jméno musí být bez mezer.                                                                                                                   |
| `#sendPrivate <jméno> <zpráva>` | Zasílá soukromou zprávu zadanému `<jméno>` s obsahem `<zpráva>`.                                                                                                                       |
| `#join <název>`                 | Připojí uživatele do diskuzní místnosti se zadaným `<název>`. Vytvoří novou místnost, pokud ještě neexistuje. Klient je po připojení automaticky připojen do místnosti __"public"__. |
| `#leave <název>`                | Odpojí uživatele z diskuzní místnosti se zadaným `<název>`.                                                                                                                            |
| `#groups`                       | Vypíše seznam všech diskuzních místností, ve kterých se klient nachází. Výpis bude v tomto formátu: `<místnost1>,<místnost2>,...`                                                      |

### Formát zprávy
Když uživatel napíše ostatním nějakou zprávu, bude se ostatním uživatelům zobrazovat následujícím způsobem:

`[<jméno>] >> <zpráva>`

>_**Poznámka:** Implementace a struktura kódu aplikace je libovolná a je zcela na vás, jak tento problém vyřešíte. Je však důležité, aby aplikace splňovala zadané požadavky._

---

## 🏆 Způsob hodnocení

Vaše implementace bude hodnocena na základě chování aplikace při testování různých scénářů. Automatizovaný testovací nástroj bude předávat vaší aplikaci různé parametry, včetně platných a neplatných, aby otestoval její chování za různých podmínek. V případě testování síťové komunikace mezi více klienty, testovací nástroj bude vytvářet virtuální klienty/servery za účelem ověření funkcionality.

Výsledné hodnocení bude záviset na celkovém počtu úspěšných testovacích případů. Počet bodů získaných z úlohy bude tedy záviset na celkové úspěšnosti při testování. Váš výsledný počet bodů bude určen následujícím vzorcem.

__VP__ = __MB__ * (__UT__ / __CPT__)

### Popis symbolů:

* __VP:__ Výsledný počet bodů.
* __MB:__ Maximální počet bodů pro danou úlohu.
* __UT:__ Počet úspěšných testovacích případů.
* __CPT:__ Celkový počet testovacích případů.

## ⚙️ Jak spustit automatizované hodnocení lokálně na svém počítači?

Automatizované hodnocení můžete spustit lokálně za účelem ověření funkčnosti vaší aplikace. K tomu slouží předpřipravený skript, který je dostupný v repozitáři tohoto úkolu. Výsledný report testování se bude nacházet v souboru ```test_report.html```.

###  Pro uživatele systému Linux:
Spusťte skript s názvem ```run_local_test.sh```.

### Pro uživatele systému Windows:
Spusťte skript s názvem ```run_local_test.bat```.


