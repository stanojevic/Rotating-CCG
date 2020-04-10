# Biaffine Parser

[![Travis](https://img.shields.io/travis/zysite/biaffine-parser.svg)](https://travis-ci.org/zysite/biaffine-parser)
[![LICENSE](https://img.shields.io/github/license/zysite/biaffine-parser.svg)](https://github.com/zysite/biaffine-parser/blob/master/LICENSE)	
[![GitHub issues](https://img.shields.io/github/issues/zysite/biaffine-parser.svg)](https://github.com/zysite/biaffine-parser/issues)		
[![GitHub stars](https://img.shields.io/github/stars/zysite/biaffine-parser.svg)](https://github.com/zysite/biaffine-parser/stargazers)

An implementation of "Deep Biaffine Attention for Neural Dependency Parsing".
Use the char branch for BERT.
Code based on https://github.com/zysite/biaffine-parser



Details and [hyperparameter choices](#Hyperparameters) are almost identical to those described in the paper, except for some training settings. Also, we do not provide a decoding algorithm to ensure well-formedness, and this does not seriously affect the results.

Another version of the implementation is available on [char](https://github.com/zysite/biaffine-parser/tree/char) branch, which replaces the tag embedding with char lstm and achieves better performance.

## Requirements

```txt
python == 3.7.0
pytorch == 1.0.0
```

## Datasets

## Everything

**Languages by Frequency**

| Language              |   Train |   Dev |   Test |   Total |
|-----------------------|---------|-------|--------|---------|
| Czech                 |  102993 | 11311 |  13203 |  127507 |
| Russian               |   53544 |  7163 |   8976 |   69683 |
| Japanese              |   47934 |  8938 |  10254 |   67126 |
| French                |   33395 |  4137 |   5249 |   42781 |
| Latin                 |   34049 |  3335 |   4300 |   41684 |
| Norwegian             |   30209 |  4300 |   4507 |   39016 |
| Finnish               |   27198 |  3239 |   4422 |   34859 |
| Korean                |   27410 |  3016 |   4276 |   34702 |
| Spanish               |   28492 |  3054 |   3147 |   34693 |
| English               |   24100 |  4277 |   5422 |   33799 |
| Ancient_Greek         |   26491 |  2156 |   2353 |   31000 |
| Estonian              |   24384 |  3125 |   3214 |   30723 |
| Arabic                |   21864 |  2895 |   3643 |   28402 |
| Polish                |   19874 |  2772 |   2827 |   25473 |
| Italian               |   20270 |  1391 |   2309 |   23970 |
| Portuguese            |   17992 |  1770 |   2681 |   22443 |
| Dutch                 |   18058 |  1394 |   1472 |   20924 |
| Romanian              |   16008 |  1804 |   1781 |   19593 |
| Old_French            |   13909 |  1842 |   1927 |   17678 |
| Hindi                 |   13304 |  1659 |   2684 |   17647 |
| Catalan               |   13123 |  1709 |   1846 |   16678 |
| German                |   13814 |   799 |   1977 |   16590 |
| Swedish               |    7041 |  1416 |   3133 |   11590 |
| Slovenian             |    8556 |   734 |   1898 |   11188 |
| Bulgarian             |    8907 |  1115 |   1116 |   11138 |
| Slovak                |    8483 |  1060 |   1061 |   10604 |
| Latvian               |    7163 |  1304 |   1453 |    9920 |
| Basque                |    5396 |  1798 |   1799 |    8993 |
| Croatian              |    6983 |   849 |   1057 |    8889 |
| Chinese               |    3997 |   500 |   2859 |    7356 |
| Ukrainian             |    5290 |   647 |    864 |    6801 |
| Turkish               |    3685 |   975 |   1975 |    6635 |
| Indonesian            |    4477 |   559 |   1557 |    6593 |
| Old_Church_Slavonic   |    4123 |  1073 |   1141 |    6337 |
| Hebrew                |    5241 |   484 |    491 |    6216 |
| Persian               |    4798 |   599 |    600 |    5997 |
| Danish                |    4383 |   564 |    565 |    5512 |
| Gothic                |    3387 |   985 |   1029 |    5401 |
| Urdu                  |    4043 |   552 |    535 |    5130 |
| Galician              |    2872 |   860 |   1261 |    4993 |
| Serbian               |    2935 |   465 |    491 |    3891 |
| Uyghur                |    1656 |   900 |    900 |    3456 |
| North_Sami            |    2257 |     0 |    865 |    3122 |
| Vietnamese            |    1400 |   800 |    800 |    3000 |
| Greek                 |    1662 |   403 |    456 |    2521 |
| Maltese               |    1123 |   433 |    518 |    2074 |
| Afrikaans             |    1315 |   194 |    425 |    1934 |
| Hindi_English         |    1448 |   225 |    225 |    1898 |
| Hungarian             |     910 |   441 |    449 |    1800 |
| Erzya                 |       0 |     0 |   1550 |    1550 |
| Telugu                |    1051 |   131 |    146 |    1328 |
| Faroese               |       0 |     0 |   1208 |    1208 |
| Kazakh                |      31 |     0 |   1047 |    1078 |
| Amharic               |       0 |     0 |   1074 |    1074 |
| Armenian              |     560 |     0 |    470 |    1030 |
| Bambara               |       0 |     0 |   1026 |    1026 |
| Irish                 |     566 |     0 |    454 |    1020 |
| Thai                  |       0 |     0 |   1000 |    1000 |
| Naija                 |       0 |     0 |    948 |     948 |
| Buryat                |      19 |     0 |    908 |     927 |
| Breton                |       0 |     0 |    888 |     888 |
| Coptic                |     370 |   203 |    267 |     840 |
| Kurmanji              |      20 |     0 |    734 |     754 |
| Cantonese             |       0 |     0 |    650 |     650 |
| Upper_Sorbian         |      23 |     0 |    623 |     646 |
| Tamil                 |     400 |    80 |    120 |     600 |
| Marathi               |     373 |    46 |     47 |     466 |
| Belarusian            |     260 |    65 |     68 |     393 |
| Komi_Zyrian           |       0 |     0 |    277 |     277 |
| Lithuanian            |     153 |    55 |     55 |     263 |
| Sanskrit              |       0 |     0 |    230 |     230 |
| Swedish_Sign_Language |      87 |    82 |     34 |     203 |
| Akkadian              |       0 |     0 |    101 |     101 |
| Yoruba                |       0 |     0 |    100 |     100 |
| Tagalog               |       0 |     0 |     55 |      55 |
| Warlpiri              |       0 |     0 |     55 |      55 |
 
**Languages Alphabetical**

| Language              |   Train |   Dev |   Test |   Total |
|-----------------------|---------|-------|--------|---------|
| Afrikaans             |    1315 |   194 |    425 |    1934 |
| Akkadian              |       0 |     0 |    101 |     101 |
| Amharic               |       0 |     0 |   1074 |    1074 |
| Ancient_Greek         |   26491 |  2156 |   2353 |   31000 |
| Arabic                |   21864 |  2895 |   3643 |   28402 |
| Armenian              |     560 |     0 |    470 |    1030 |
| Bambara               |       0 |     0 |   1026 |    1026 |
| Basque                |    5396 |  1798 |   1799 |    8993 |
| Belarusian            |     260 |    65 |     68 |     393 |
| Breton                |       0 |     0 |    888 |     888 |
| Bulgarian             |    8907 |  1115 |   1116 |   11138 |
| Buryat                |      19 |     0 |    908 |     927 |
| Cantonese             |       0 |     0 |    650 |     650 |
| Catalan               |   13123 |  1709 |   1846 |   16678 |
| Chinese               |    3997 |   500 |   2859 |    7356 |
| Coptic                |     370 |   203 |    267 |     840 |
| Croatian              |    6983 |   849 |   1057 |    8889 |
| Czech                 |  102993 | 11311 |  13203 |  127507 |
| Danish                |    4383 |   564 |    565 |    5512 |
| Dutch                 |   18058 |  1394 |   1472 |   20924 |
| English               |   24100 |  4277 |   5422 |   33799 |
| Erzya                 |       0 |     0 |   1550 |    1550 |
| Estonian              |   24384 |  3125 |   3214 |   30723 |
| Faroese               |       0 |     0 |   1208 |    1208 |
| Finnish               |   27198 |  3239 |   4422 |   34859 |
| French                |   33395 |  4137 |   5249 |   42781 |
| Galician              |    2872 |   860 |   1261 |    4993 |
| German                |   13814 |   799 |   1977 |   16590 |
| Gothic                |    3387 |   985 |   1029 |    5401 |
| Greek                 |    1662 |   403 |    456 |    2521 |
| Hebrew                |    5241 |   484 |    491 |    6216 |
| Hindi                 |   13304 |  1659 |   2684 |   17647 |
| Hindi_English         |    1448 |   225 |    225 |    1898 |
| Hungarian             |     910 |   441 |    449 |    1800 |
| Indonesian            |    4477 |   559 |   1557 |    6593 |
| Irish                 |     566 |     0 |    454 |    1020 |
| Italian               |   20270 |  1391 |   2309 |   23970 |
| Japanese              |   47934 |  8938 |  10254 |   67126 |
| Kazakh                |      31 |     0 |   1047 |    1078 |
| Komi_Zyrian           |       0 |     0 |    277 |     277 |
| Korean                |   27410 |  3016 |   4276 |   34702 |
| Kurmanji              |      20 |     0 |    734 |     754 |
| Latin                 |   34049 |  3335 |   4300 |   41684 |
| Latvian               |    7163 |  1304 |   1453 |    9920 |
| Lithuanian            |     153 |    55 |     55 |     263 |
| Maltese               |    1123 |   433 |    518 |    2074 |
| Marathi               |     373 |    46 |     47 |     466 |
| Naija                 |       0 |     0 |    948 |     948 |
| North_Sami            |    2257 |     0 |    865 |    3122 |
| Norwegian             |   30209 |  4300 |   4507 |   39016 |
| Old_Church_Slavonic   |    4123 |  1073 |   1141 |    6337 |
| Old_French            |   13909 |  1842 |   1927 |   17678 |
| Persian               |    4798 |   599 |    600 |    5997 |
| Polish                |   19874 |  2772 |   2827 |   25473 |
| Portuguese            |   17992 |  1770 |   2681 |   22443 |
| Romanian              |   16008 |  1804 |   1781 |   19593 |
| Russian               |   53544 |  7163 |   8976 |   69683 |
| Sanskrit              |       0 |     0 |    230 |     230 |
| Serbian               |    2935 |   465 |    491 |    3891 |
| Slovak                |    8483 |  1060 |   1061 |   10604 |
| Slovenian             |    8556 |   734 |   1898 |   11188 |
| Spanish               |   28492 |  3054 |   3147 |   34693 |
| Swedish               |    7041 |  1416 |   3133 |   11590 |
| Swedish_Sign_Language |      87 |    82 |     34 |     203 |
| Tagalog               |       0 |     0 |     55 |      55 |
| Tamil                 |     400 |    80 |    120 |     600 |
| Telugu                |    1051 |   131 |    146 |    1328 |
| Thai                  |       0 |     0 |   1000 |    1000 |
| Turkish               |    3685 |   975 |   1975 |    6635 |
| Ukrainian             |    5290 |   647 |    864 |    6801 |
| Upper_Sorbian         |      23 |     0 |    623 |     646 |
| Urdu                  |    4043 |   552 |    535 |    5130 |
| Uyghur                |    1656 |   900 |    900 |    3456 |
| Vietnamese            |    1400 |   800 |    800 |    3000 |
| Warlpiri              |       0 |     0 |     55 |      55 |
| Yoruba                |       0 |     0 |    100 |     100 |
 
**Corpora by Frequency**

| Corpus                        |   Train |   Dev |   Test |   Total |
|-------------------------------|---------|-------|--------|---------|
| UD_Czech-PDT                  |   68495 |  9270 |  10148 |   87913 |
| UD_Russian-SynTagRus          |   48814 |  6584 |   6491 |   61889 |
| UD_Japanese-BCCWJ             |   40801 |  8427 |   7881 |   57109 |
| UD_Estonian-EDT               |   24384 |  3125 |   3214 |   30723 |
| UD_Korean-Kaist               |   23010 |  2066 |   2287 |   27363 |
| UD_Czech-CAC                  |   23478 |   603 |    628 |   24709 |
| UD_Latin-ITTB                 |   16809 |  2101 |   2101 |   21011 |
| UD_Norwegian-Bokmaal          |   15696 |  2410 |   1939 |   20045 |
| UD_Arabic-NYUAD               |   15789 |  1986 |   1963 |   19738 |
| UD_Finnish-FTB                |   14981 |  1875 |   1867 |   18723 |
| UD_French-FTB                 |   14759 |  1235 |   2541 |   18535 |
| UD_Latin-PROIEL               |   15906 |  1234 |   1260 |   18400 |
| UD_Spanish-AnCora             |   14305 |  1654 |   1721 |   17680 |
| UD_Old_French-SRCMF           |   13909 |  1842 |   1927 |   17678 |
| UD_Norwegian-Nynorsk          |   14174 |  1890 |   1511 |   17575 |
| UD_Polish-LFG                 |   13774 |  1745 |   1727 |   17246 |
| UD_Ancient_Greek-PROIEL       |   15015 |  1019 |   1047 |   17081 |
| UD_Catalan-AnCora             |   13123 |  1709 |   1846 |   16678 |
| UD_Hindi-HDTB                 |   13304 |  1659 |   1684 |   16647 |
| UD_English-EWT                |   12543 |  2002 |   2077 |   16622 |
| UD_French-GSD                 |   14449 |  1476 |    416 |   16341 |
| UD_Spanish-GSD                |   14187 |  1400 |    426 |   16013 |
| UD_German-GSD                 |   13814 |   799 |    977 |   15590 |
| UD_Finnish-TDT                |   12217 |  1364 |   1555 |   15136 |
| UD_Italian-ISDT               |   13121 |   564 |    482 |   14167 |
| UD_Ancient_Greek-Perseus      |   11476 |  1137 |   1306 |   13919 |
| UD_Dutch-Alpino               |   12269 |   718 |    596 |   13583 |
| UD_Czech-FicTree              |   10160 |  1309 |   1291 |   12760 |
| UD_Portuguese-GSD             |    9664 |  1210 |   1204 |   12078 |
| UD_Bulgarian-BTB              |    8907 |  1115 |   1116 |   11138 |
| UD_Slovak-SNK                 |    8483 |  1060 |   1061 |   10604 |
| UD_Romanian-Nonstandard       |    7965 |  1052 |   1052 |   10069 |
| UD_Latvian-LVTB               |    7163 |  1304 |   1453 |    9920 |
| UD_Romanian-RRT               |    8043 |   752 |    729 |    9524 |
| UD_Portuguese-Bosque          |    8328 |   560 |    477 |    9365 |
| UD_Basque-BDT                 |    5396 |  1798 |   1799 |    8993 |
| UD_Croatian-SET               |    6983 |   849 |   1057 |    8889 |
| UD_Polish-SZ                  |    6100 |  1027 |   1100 |    8227 |
| UD_Japanese-GSD               |    7133 |   511 |    551 |    8195 |
| UD_Slovenian-SSJ              |    6478 |   734 |    788 |    8000 |
| UD_Arabic-PADT                |    6075 |   909 |    680 |    7664 |
| UD_Dutch-LassySmall           |    5789 |   676 |    876 |    7341 |
| UD_Ukrainian-IU               |    5290 |   647 |    864 |    6801 |
| UD_Italian-PoSTWITA           |    5368 |   671 |    674 |    6713 |
| UD_Korean-GSD                 |    4400 |   950 |    989 |    6339 |
| UD_Old_Church_Slavonic-PROIEL |    4123 |  1073 |   1141 |    6337 |
| UD_Hebrew-HTB                 |    5241 |   484 |    491 |    6216 |
| UD_Swedish-Talbanken          |    4303 |   504 |   1219 |    6026 |
| UD_Persian-Seraji             |    4798 |   599 |    600 |    5997 |
| UD_Turkish-IMST               |    3685 |   975 |    975 |    5635 |
| UD_Indonesian-GSD             |    4477 |   559 |    557 |    5593 |
| UD_Danish-DDT                 |    4383 |   564 |    565 |    5512 |
| UD_Gothic-PROIEL              |    3387 |   985 |   1029 |    5401 |
| UD_Urdu-UDTB                  |    4043 |   552 |    535 |    5130 |
| UD_English-ESL                |    4124 |   500 |    500 |    5124 |
| UD_Russian-GSD                |    3850 |   579 |    601 |    5030 |
| UD_Chinese-GSD                |    3997 |   500 |    500 |    4997 |
| UD_English-LinES              |    2738 |   912 |    914 |    4564 |
| UD_Swedish-LinES              |    2738 |   912 |    914 |    4564 |
| UD_English-GUM                |    2914 |   707 |    778 |    4399 |
| UD_Galician-CTG               |    2272 |   860 |    861 |    3993 |
| UD_Serbian-SET                |    2935 |   465 |    491 |    3891 |
| UD_Uyghur-UDT                 |    1656 |   900 |    900 |    3456 |
| UD_Slovenian-SST              |    2078 |     0 |   1110 |    3188 |
| UD_North_Sami-Giella          |    2257 |     0 |    865 |    3122 |
| UD_French-Sequoia             |    2231 |   412 |    456 |    3099 |
| UD_Vietnamese-VTB             |    1400 |   800 |    800 |    3000 |
| UD_French-Spoken              |    1153 |   907 |    726 |    2786 |
| UD_Greek-GDT                  |    1662 |   403 |    456 |    2521 |
| UD_Latin-Perseus              |    1334 |     0 |    939 |    2273 |
| UD_English-ParTUT             |    1781 |   156 |    153 |    2090 |
| UD_Italian-ParTUT             |    1781 |   156 |    153 |    2090 |
| UD_Maltese-MUDT               |    1123 |   433 |    518 |    2074 |
| UD_Afrikaans-AfriBooms        |    1315 |   194 |    425 |    1934 |
| UD_Hindi_English-HIENCS       |    1448 |   225 |    225 |    1898 |
| UD_Hungarian-Szeged           |     910 |   441 |    449 |    1800 |
| UD_Russian-Taiga              |     880 |     0 |    884 |    1764 |
| UD_Erzya-JR                   |       0 |     0 |   1550 |    1550 |
| UD_Norwegian-NynorskLIA       |     339 |     0 |   1057 |    1396 |
| UD_Telugu-MTG                 |    1051 |   131 |    146 |    1328 |
| UD_Faroese-OFT                |       0 |     0 |   1208 |    1208 |
| UD_Czech-CLTT                 |     860 |   129 |    136 |    1125 |
| UD_Kazakh-KTB                 |      31 |     0 |   1047 |    1078 |
| UD_Amharic-ATT                |       0 |     0 |   1074 |    1074 |
| UD_Armenian-ArmTDP            |     560 |     0 |    470 |    1030 |
| UD_Bambara-CRB                |       0 |     0 |   1026 |    1026 |
| UD_French-ParTUT              |     803 |   107 |    110 |    1020 |
| UD_Irish-IDT                  |     566 |     0 |    454 |    1020 |
| UD_Galician-TreeGal           |     600 |     0 |    400 |    1000 |
| UD_Arabic-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Chinese-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Czech-PUD                  |       0 |     0 |   1000 |    1000 |
| UD_English-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Finnish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_French-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_German-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Hindi-PUD                  |       0 |     0 |   1000 |    1000 |
| UD_Indonesian-PUD             |       0 |     0 |   1000 |    1000 |
| UD_Italian-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Japanese-PUD               |       0 |     0 |   1000 |    1000 |
| UD_Korean-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Portuguese-PUD             |       0 |     0 |   1000 |    1000 |
| UD_Russian-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Spanish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Swedish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Thai-PUD                   |       0 |     0 |   1000 |    1000 |
| UD_Turkish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Naija-NSC                  |       0 |     0 |    948 |     948 |
| UD_Buryat-BDT                 |      19 |     0 |    908 |     927 |
| UD_Chinese-HK                 |       0 |     0 |    908 |     908 |
| UD_Breton-KEB                 |       0 |     0 |    888 |     888 |
| UD_Coptic-Scriptorium         |     370 |   203 |    267 |     840 |
| UD_Japanese-Modern            |       0 |     0 |    822 |     822 |
| UD_Kurmanji-MG                |      20 |     0 |    734 |     754 |
| UD_Cantonese-HK               |       0 |     0 |    650 |     650 |
| UD_Upper_Sorbian-UFAL         |      23 |     0 |    623 |     646 |
| UD_Tamil-TTB                  |     400 |    80 |    120 |     600 |
| UD_Marathi-UFAL               |     373 |    46 |     47 |     466 |
| UD_Chinese-CFL                |       0 |     0 |    451 |     451 |
| UD_Belarusian-HSE             |     260 |    65 |     68 |     393 |
| UD_Lithuanian-HSE             |     153 |    55 |     55 |     263 |
| UD_Sanskrit-UFAL              |       0 |     0 |    230 |     230 |
| UD_Swedish_Sign_Language-SSLC |      87 |    82 |     34 |     203 |
| UD_Komi_Zyrian-Lattice        |       0 |     0 |    190 |     190 |
| UD_Akkadian-PISANDUB          |       0 |     0 |    101 |     101 |
| UD_Yoruba-YTB                 |       0 |     0 |    100 |     100 |
| UD_Komi_Zyrian-IKDP           |       0 |     0 |     87 |      87 |
| UD_Tagalog-TRG                |       0 |     0 |     55 |      55 |
| UD_Warlpiri-UFAL              |       0 |     0 |     55 |      55 |
 
**Corpora Alphabetical**

| Corpus                        |   Train |   Dev |   Test |   Total |
|-------------------------------|---------|-------|--------|---------|
| UD_Afrikaans-AfriBooms        |    1315 |   194 |    425 |    1934 |
| UD_Akkadian-PISANDUB          |       0 |     0 |    101 |     101 |
| UD_Amharic-ATT                |       0 |     0 |   1074 |    1074 |
| UD_Ancient_Greek-PROIEL       |   15015 |  1019 |   1047 |   17081 |
| UD_Ancient_Greek-Perseus      |   11476 |  1137 |   1306 |   13919 |
| UD_Arabic-NYUAD               |   15789 |  1986 |   1963 |   19738 |
| UD_Arabic-PADT                |    6075 |   909 |    680 |    7664 |
| UD_Arabic-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Armenian-ArmTDP            |     560 |     0 |    470 |    1030 |
| UD_Bambara-CRB                |       0 |     0 |   1026 |    1026 |
| UD_Basque-BDT                 |    5396 |  1798 |   1799 |    8993 |
| UD_Belarusian-HSE             |     260 |    65 |     68 |     393 |
| UD_Breton-KEB                 |       0 |     0 |    888 |     888 |
| UD_Bulgarian-BTB              |    8907 |  1115 |   1116 |   11138 |
| UD_Buryat-BDT                 |      19 |     0 |    908 |     927 |
| UD_Cantonese-HK               |       0 |     0 |    650 |     650 |
| UD_Catalan-AnCora             |   13123 |  1709 |   1846 |   16678 |
| UD_Chinese-CFL                |       0 |     0 |    451 |     451 |
| UD_Chinese-GSD                |    3997 |   500 |    500 |    4997 |
| UD_Chinese-HK                 |       0 |     0 |    908 |     908 |
| UD_Chinese-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Coptic-Scriptorium         |     370 |   203 |    267 |     840 |
| UD_Croatian-SET               |    6983 |   849 |   1057 |    8889 |
| UD_Czech-CAC                  |   23478 |   603 |    628 |   24709 |
| UD_Czech-CLTT                 |     860 |   129 |    136 |    1125 |
| UD_Czech-FicTree              |   10160 |  1309 |   1291 |   12760 |
| UD_Czech-PDT                  |   68495 |  9270 |  10148 |   87913 |
| UD_Czech-PUD                  |       0 |     0 |   1000 |    1000 |
| UD_Danish-DDT                 |    4383 |   564 |    565 |    5512 |
| UD_Dutch-Alpino               |   12269 |   718 |    596 |   13583 |
| UD_Dutch-LassySmall           |    5789 |   676 |    876 |    7341 |
| UD_English-ESL                |    4124 |   500 |    500 |    5124 |
| UD_English-EWT                |   12543 |  2002 |   2077 |   16622 |
| UD_English-GUM                |    2914 |   707 |    778 |    4399 |
| UD_English-LinES              |    2738 |   912 |    914 |    4564 |
| UD_English-PUD                |       0 |     0 |   1000 |    1000 |
| UD_English-ParTUT             |    1781 |   156 |    153 |    2090 |
| UD_Erzya-JR                   |       0 |     0 |   1550 |    1550 |
| UD_Estonian-EDT               |   24384 |  3125 |   3214 |   30723 |
| UD_Faroese-OFT                |       0 |     0 |   1208 |    1208 |
| UD_Finnish-FTB                |   14981 |  1875 |   1867 |   18723 |
| UD_Finnish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Finnish-TDT                |   12217 |  1364 |   1555 |   15136 |
| UD_French-FTB                 |   14759 |  1235 |   2541 |   18535 |
| UD_French-GSD                 |   14449 |  1476 |    416 |   16341 |
| UD_French-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_French-ParTUT              |     803 |   107 |    110 |    1020 |
| UD_French-Sequoia             |    2231 |   412 |    456 |    3099 |
| UD_French-Spoken              |    1153 |   907 |    726 |    2786 |
| UD_Galician-CTG               |    2272 |   860 |    861 |    3993 |
| UD_Galician-TreeGal           |     600 |     0 |    400 |    1000 |
| UD_German-GSD                 |   13814 |   799 |    977 |   15590 |
| UD_German-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Gothic-PROIEL              |    3387 |   985 |   1029 |    5401 |
| UD_Greek-GDT                  |    1662 |   403 |    456 |    2521 |
| UD_Hebrew-HTB                 |    5241 |   484 |    491 |    6216 |
| UD_Hindi-HDTB                 |   13304 |  1659 |   1684 |   16647 |
| UD_Hindi-PUD                  |       0 |     0 |   1000 |    1000 |
| UD_Hindi_English-HIENCS       |    1448 |   225 |    225 |    1898 |
| UD_Hungarian-Szeged           |     910 |   441 |    449 |    1800 |
| UD_Indonesian-GSD             |    4477 |   559 |    557 |    5593 |
| UD_Indonesian-PUD             |       0 |     0 |   1000 |    1000 |
| UD_Irish-IDT                  |     566 |     0 |    454 |    1020 |
| UD_Italian-ISDT               |   13121 |   564 |    482 |   14167 |
| UD_Italian-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Italian-ParTUT             |    1781 |   156 |    153 |    2090 |
| UD_Italian-PoSTWITA           |    5368 |   671 |    674 |    6713 |
| UD_Japanese-BCCWJ             |   40801 |  8427 |   7881 |   57109 |
| UD_Japanese-GSD               |    7133 |   511 |    551 |    8195 |
| UD_Japanese-Modern            |       0 |     0 |    822 |     822 |
| UD_Japanese-PUD               |       0 |     0 |   1000 |    1000 |
| UD_Kazakh-KTB                 |      31 |     0 |   1047 |    1078 |
| UD_Komi_Zyrian-IKDP           |       0 |     0 |     87 |      87 |
| UD_Komi_Zyrian-Lattice        |       0 |     0 |    190 |     190 |
| UD_Korean-GSD                 |    4400 |   950 |    989 |    6339 |
| UD_Korean-Kaist               |   23010 |  2066 |   2287 |   27363 |
| UD_Korean-PUD                 |       0 |     0 |   1000 |    1000 |
| UD_Kurmanji-MG                |      20 |     0 |    734 |     754 |
| UD_Latin-ITTB                 |   16809 |  2101 |   2101 |   21011 |
| UD_Latin-PROIEL               |   15906 |  1234 |   1260 |   18400 |
| UD_Latin-Perseus              |    1334 |     0 |    939 |    2273 |
| UD_Latvian-LVTB               |    7163 |  1304 |   1453 |    9920 |
| UD_Lithuanian-HSE             |     153 |    55 |     55 |     263 |
| UD_Maltese-MUDT               |    1123 |   433 |    518 |    2074 |
| UD_Marathi-UFAL               |     373 |    46 |     47 |     466 |
| UD_Naija-NSC                  |       0 |     0 |    948 |     948 |
| UD_North_Sami-Giella          |    2257 |     0 |    865 |    3122 |
| UD_Norwegian-Bokmaal          |   15696 |  2410 |   1939 |   20045 |
| UD_Norwegian-Nynorsk          |   14174 |  1890 |   1511 |   17575 |
| UD_Norwegian-NynorskLIA       |     339 |     0 |   1057 |    1396 |
| UD_Old_Church_Slavonic-PROIEL |    4123 |  1073 |   1141 |    6337 |
| UD_Old_French-SRCMF           |   13909 |  1842 |   1927 |   17678 |
| UD_Persian-Seraji             |    4798 |   599 |    600 |    5997 |
| UD_Polish-LFG                 |   13774 |  1745 |   1727 |   17246 |
| UD_Polish-SZ                  |    6100 |  1027 |   1100 |    8227 |
| UD_Portuguese-Bosque          |    8328 |   560 |    477 |    9365 |
| UD_Portuguese-GSD             |    9664 |  1210 |   1204 |   12078 |
| UD_Portuguese-PUD             |       0 |     0 |   1000 |    1000 |
| UD_Romanian-Nonstandard       |    7965 |  1052 |   1052 |   10069 |
| UD_Romanian-RRT               |    8043 |   752 |    729 |    9524 |
| UD_Russian-GSD                |    3850 |   579 |    601 |    5030 |
| UD_Russian-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Russian-SynTagRus          |   48814 |  6584 |   6491 |   61889 |
| UD_Russian-Taiga              |     880 |     0 |    884 |    1764 |
| UD_Sanskrit-UFAL              |       0 |     0 |    230 |     230 |
| UD_Serbian-SET                |    2935 |   465 |    491 |    3891 |
| UD_Slovak-SNK                 |    8483 |  1060 |   1061 |   10604 |
| UD_Slovenian-SSJ              |    6478 |   734 |    788 |    8000 |
| UD_Slovenian-SST              |    2078 |     0 |   1110 |    3188 |
| UD_Spanish-AnCora             |   14305 |  1654 |   1721 |   17680 |
| UD_Spanish-GSD                |   14187 |  1400 |    426 |   16013 |
| UD_Spanish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Swedish-LinES              |    2738 |   912 |    914 |    4564 |
| UD_Swedish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Swedish-Talbanken          |    4303 |   504 |   1219 |    6026 |
| UD_Swedish_Sign_Language-SSLC |      87 |    82 |     34 |     203 |
| UD_Tagalog-TRG                |       0 |     0 |     55 |      55 |
| UD_Tamil-TTB                  |     400 |    80 |    120 |     600 |
| UD_Telugu-MTG                 |    1051 |   131 |    146 |    1328 |
| UD_Thai-PUD                   |       0 |     0 |   1000 |    1000 |
| UD_Turkish-IMST               |    3685 |   975 |    975 |    5635 |
| UD_Turkish-PUD                |       0 |     0 |   1000 |    1000 |
| UD_Ukrainian-IU               |    5290 |   647 |    864 |    6801 |
| UD_Upper_Sorbian-UFAL         |      23 |     0 |    623 |     646 |
| UD_Urdu-UDTB                  |    4043 |   552 |    535 |    5130 |
| UD_Uyghur-UDT                 |    1656 |   900 |    900 |    3456 |
| UD_Vietnamese-VTB             |    1400 |   800 |    800 |    3000 |
| UD_Warlpiri-UFAL              |       0 |     0 |     55 |      55 |
| UD_Yoruba-YTB                 |       0 |     0 |    100 |     100 |
## The Ones Used

**Languages by Frequency**

| Language   |   Train |   Dev |   Test |   Total |
|------------|---------|-------|--------|---------|
| Czech      |  102993 | 11311 |  13203 |  127507 |
| Russian    |   53544 |  7163 |   8976 |   69683 |
| Japanese   |   47934 |  8938 |  10254 |   67126 |
| French     |   33395 |  4137 |   5249 |   42781 |
| Latin      |   34049 |  3335 |   4300 |   41684 |
| Norwegian  |   30209 |  4300 |   4507 |   39016 |
| Finnish    |   27198 |  3239 |   4422 |   34859 |
| Korean     |   27410 |  3016 |   4276 |   34702 |
| Spanish    |   28492 |  3054 |   3147 |   34693 |
| English    |   24100 |  4277 |   5422 |   33799 |
| Estonian   |   24384 |  3125 |   3214 |   30723 |
| Arabic     |   21864 |  2895 |   3643 |   28402 |
| Polish     |   19874 |  2772 |   2827 |   25473 |
| Italian    |   20270 |  1391 |   2309 |   23970 |
| Portuguese |   17992 |  1770 |   2681 |   22443 |
| Dutch      |   18058 |  1394 |   1472 |   20924 |
| Romanian   |   16008 |  1804 |   1781 |   19593 |
| Hindi      |   13304 |  1659 |   2684 |   17647 |
| Catalan    |   13123 |  1709 |   1846 |   16678 |
| German     |   13814 |   799 |   1977 |   16590 |
| Swedish    |    7041 |  1416 |   3133 |   11590 |
| Slovenian  |    8556 |   734 |   1898 |   11188 |
| Bulgarian  |    8907 |  1115 |   1116 |   11138 |
| Slovak     |    8483 |  1060 |   1061 |   10604 |
| Latvian    |    7163 |  1304 |   1453 |    9920 |
| Basque     |    5396 |  1798 |   1799 |    8993 |
| Croatian   |    6983 |   849 |   1057 |    8889 |
| Chinese    |    3997 |   500 |   2859 |    7356 |
| Turkish    |    3685 |   975 |   1975 |    6635 |
| Indonesian |    4477 |   559 |   1557 |    6593 |
| Hebrew     |    5241 |   484 |    491 |    6216 |
| Persian    |    4798 |   599 |    600 |    5997 |
| Danish     |    4383 |   564 |    565 |    5512 |
| Urdu       |    4043 |   552 |    535 |    5130 |
| Galician   |    2872 |   860 |   1261 |    4993 |
| Serbian    |    2935 |   465 |    491 |    3891 |
| Vietnamese |    1400 |   800 |    800 |    3000 |
| Greek      |    1662 |   403 |    456 |    2521 |
| Afrikaans  |    1315 |   194 |    425 |    1934 |
| Hungarian  |     910 |   441 |    449 |    1800 |
| Telugu     |    1051 |   131 |    146 |    1328 |
| Kazakh     |      31 |     0 |   1047 |    1078 |
| Armenian   |     560 |     0 |    470 |    1030 |
| Irish      |     566 |     0 |    454 |    1020 |
| Tamil      |     400 |    80 |    120 |     600 |
| Marathi    |     373 |    46 |     47 |     466 |
| Belarusian |     260 |    65 |     68 |     393 |
| Lithuanian |     153 |    55 |     55 |     263 |
 
**Languages Alphabetical**

| Language   |   Train |   Dev |   Test |   Total |
|------------|---------|-------|--------|---------|
| Afrikaans  |    1315 |   194 |    425 |    1934 |
| Arabic     |   21864 |  2895 |   3643 |   28402 |
| Armenian   |     560 |     0 |    470 |    1030 |
| Basque     |    5396 |  1798 |   1799 |    8993 |
| Belarusian |     260 |    65 |     68 |     393 |
| Bulgarian  |    8907 |  1115 |   1116 |   11138 |
| Catalan    |   13123 |  1709 |   1846 |   16678 |
| Chinese    |    3997 |   500 |   2859 |    7356 |
| Croatian   |    6983 |   849 |   1057 |    8889 |
| Czech      |  102993 | 11311 |  13203 |  127507 |
| Danish     |    4383 |   564 |    565 |    5512 |
| Dutch      |   18058 |  1394 |   1472 |   20924 |
| English    |   24100 |  4277 |   5422 |   33799 |
| Estonian   |   24384 |  3125 |   3214 |   30723 |
| Finnish    |   27198 |  3239 |   4422 |   34859 |
| French     |   33395 |  4137 |   5249 |   42781 |
| Galician   |    2872 |   860 |   1261 |    4993 |
| German     |   13814 |   799 |   1977 |   16590 |
| Greek      |    1662 |   403 |    456 |    2521 |
| Hebrew     |    5241 |   484 |    491 |    6216 |
| Hindi      |   13304 |  1659 |   2684 |   17647 |
| Hungarian  |     910 |   441 |    449 |    1800 |
| Indonesian |    4477 |   559 |   1557 |    6593 |
| Irish      |     566 |     0 |    454 |    1020 |
| Italian    |   20270 |  1391 |   2309 |   23970 |
| Japanese   |   47934 |  8938 |  10254 |   67126 |
| Kazakh     |      31 |     0 |   1047 |    1078 |
| Korean     |   27410 |  3016 |   4276 |   34702 |
| Latin      |   34049 |  3335 |   4300 |   41684 |
| Latvian    |    7163 |  1304 |   1453 |    9920 |
| Lithuanian |     153 |    55 |     55 |     263 |
| Marathi    |     373 |    46 |     47 |     466 |
| Norwegian  |   30209 |  4300 |   4507 |   39016 |
| Persian    |    4798 |   599 |    600 |    5997 |
| Polish     |   19874 |  2772 |   2827 |   25473 |
| Portuguese |   17992 |  1770 |   2681 |   22443 |
| Romanian   |   16008 |  1804 |   1781 |   19593 |
| Russian    |   53544 |  7163 |   8976 |   69683 |
| Serbian    |    2935 |   465 |    491 |    3891 |
| Slovak     |    8483 |  1060 |   1061 |   10604 |
| Slovenian  |    8556 |   734 |   1898 |   11188 |
| Spanish    |   28492 |  3054 |   3147 |   34693 |
| Swedish    |    7041 |  1416 |   3133 |   11590 |
| Tamil      |     400 |    80 |    120 |     600 |
| Telugu     |    1051 |   131 |    146 |    1328 |
| Turkish    |    3685 |   975 |   1975 |    6635 |
| Urdu       |    4043 |   552 |    535 |    5130 |
| Vietnamese |    1400 |   800 |    800 |    3000 |
 
**Corpora by Frequency**

| Corpus                 |   Train |   Dev |   Test |   Total |
|------------------------|---------|-------|--------|---------|
| UD_Czech-PDT           |   68495 |  9270 |  10148 |   87913 |
| UD_Russian-SynTagRus   |   48814 |  6584 |   6491 |   61889 |
| UD_Estonian-EDT        |   24384 |  3125 |   3214 |   30723 |
| UD_Korean-Kaist        |   23010 |  2066 |   2287 |   27363 |
| UD_Czech-CAC           |   23478 |   603 |    628 |   24709 |
| UD_Latin-ITTB          |   16809 |  2101 |   2101 |   21011 |
| UD_Norwegian-Bokmaal   |   15696 |  2410 |   1939 |   20045 |
| UD_Arabic-NYUAD        |   15789 |  1986 |   1963 |   19738 |
| UD_Finnish-FTB         |   14981 |  1875 |   1867 |   18723 |
| UD_Latin-PROIEL        |   15906 |  1234 |   1260 |   18400 |
| UD_Spanish-AnCora      |   14305 |  1654 |   1721 |   17680 |
| UD_Norwegian-Nynorsk   |   14174 |  1890 |   1511 |   17575 |
| UD_Polish-LFG          |   13774 |  1745 |   1727 |   17246 |
| UD_Catalan-AnCora      |   13123 |  1709 |   1846 |   16678 |
| UD_Hindi-HDTB          |   13304 |  1659 |   1684 |   16647 |
| UD_English-EWT         |   12543 |  2002 |   2077 |   16622 |
| UD_French-GSD          |   14449 |  1476 |    416 |   16341 |
| UD_Spanish-GSD         |   14187 |  1400 |    426 |   16013 |
| UD_German-GSD          |   13814 |   799 |    977 |   15590 |
| UD_Finnish-TDT         |   12217 |  1364 |   1555 |   15136 |
| UD_Italian-ISDT        |   13121 |   564 |    482 |   14167 |
| UD_Dutch-Alpino        |   12269 |   718 |    596 |   13583 |
| UD_Czech-FicTree       |   10160 |  1309 |   1291 |   12760 |
| UD_Portuguese-GSD      |    9664 |  1210 |   1204 |   12078 |
| UD_Bulgarian-BTB       |    8907 |  1115 |   1116 |   11138 |
| UD_Slovak-SNK          |    8483 |  1060 |   1061 |   10604 |
| UD_Latvian-LVTB        |    7163 |  1304 |   1453 |    9920 |
| UD_Romanian-RRT        |    8043 |   752 |    729 |    9524 |
| UD_Portuguese-Bosque   |    8328 |   560 |    477 |    9365 |
| UD_Basque-BDT          |    5396 |  1798 |   1799 |    8993 |
| UD_Croatian-SET        |    6983 |   849 |   1057 |    8889 |
| UD_Polish-SZ           |    6100 |  1027 |   1100 |    8227 |
| UD_Japanese-GSD        |    7133 |   511 |    551 |    8195 |
| UD_Slovenian-SSJ       |    6478 |   734 |    788 |    8000 |
| UD_Arabic-PADT         |    6075 |   909 |    680 |    7664 |
| UD_Dutch-LassySmall    |    5789 |   676 |    876 |    7341 |
| UD_Korean-GSD          |    4400 |   950 |    989 |    6339 |
| UD_Hebrew-HTB          |    5241 |   484 |    491 |    6216 |
| UD_Swedish-Talbanken   |    4303 |   504 |   1219 |    6026 |
| UD_Persian-Seraji      |    4798 |   599 |    600 |    5997 |
| UD_Turkish-IMST        |    3685 |   975 |    975 |    5635 |
| UD_Indonesian-GSD      |    4477 |   559 |    557 |    5593 |
| UD_Danish-DDT          |    4383 |   564 |    565 |    5512 |
| UD_Urdu-UDTB           |    4043 |   552 |    535 |    5130 |
| UD_English-ESL         |    4124 |   500 |    500 |    5124 |
| UD_Russian-GSD         |    3850 |   579 |    601 |    5030 |
| UD_Chinese-GSD         |    3997 |   500 |    500 |    4997 |
| UD_English-LinES       |    2738 |   912 |    914 |    4564 |
| UD_Swedish-LinES       |    2738 |   912 |    914 |    4564 |
| UD_English-GUM         |    2914 |   707 |    778 |    4399 |
| UD_Galician-CTG        |    2272 |   860 |    861 |    3993 |
| UD_Serbian-SET         |    2935 |   465 |    491 |    3891 |
| UD_French-Sequoia      |    2231 |   412 |    456 |    3099 |
| UD_Vietnamese-VTB      |    1400 |   800 |    800 |    3000 |
| UD_Greek-GDT           |    1662 |   403 |    456 |    2521 |
| UD_Latin-Perseus       |    1334 |     0 |    939 |    2273 |
| UD_English-ParTUT      |    1781 |   156 |    153 |    2090 |
| UD_Italian-ParTUT      |    1781 |   156 |    153 |    2090 |
| UD_Afrikaans-AfriBooms |    1315 |   194 |    425 |    1934 |
| UD_Hungarian-Szeged    |     910 |   441 |    449 |    1800 |
| UD_Telugu-MTG          |    1051 |   131 |    146 |    1328 |
| UD_Czech-CLTT          |     860 |   129 |    136 |    1125 |
| UD_Kazakh-KTB          |      31 |     0 |   1047 |    1078 |
| UD_Armenian-ArmTDP     |     560 |     0 |    470 |    1030 |
| UD_French-ParTUT       |     803 |   107 |    110 |    1020 |
| UD_Irish-IDT           |     566 |     0 |    454 |    1020 |
| UD_Galician-TreeGal    |     600 |     0 |    400 |    1000 |
| UD_Tamil-TTB           |     400 |    80 |    120 |     600 |
| UD_Marathi-UFAL        |     373 |    46 |     47 |     466 |
| UD_Belarusian-HSE      |     260 |    65 |     68 |     393 |
| UD_Lithuanian-HSE      |     153 |    55 |     55 |     263 |
 
**Corpora Alphabetical**

| Corpus                 |   Train |   Dev |   Test |   Total |
|------------------------|---------|-------|--------|---------|
| UD_Afrikaans-AfriBooms |    1315 |   194 |    425 |    1934 |
| UD_Arabic-NYUAD        |   15789 |  1986 |   1963 |   19738 |
| UD_Arabic-PADT         |    6075 |   909 |    680 |    7664 |
| UD_Armenian-ArmTDP     |     560 |     0 |    470 |    1030 |
| UD_Basque-BDT          |    5396 |  1798 |   1799 |    8993 |
| UD_Belarusian-HSE      |     260 |    65 |     68 |     393 |
| UD_Bulgarian-BTB       |    8907 |  1115 |   1116 |   11138 |
| UD_Catalan-AnCora      |   13123 |  1709 |   1846 |   16678 |
| UD_Chinese-GSD         |    3997 |   500 |    500 |    4997 |
| UD_Croatian-SET        |    6983 |   849 |   1057 |    8889 |
| UD_Czech-CAC           |   23478 |   603 |    628 |   24709 |
| UD_Czech-CLTT          |     860 |   129 |    136 |    1125 |
| UD_Czech-FicTree       |   10160 |  1309 |   1291 |   12760 |
| UD_Czech-PDT           |   68495 |  9270 |  10148 |   87913 |
| UD_Danish-DDT          |    4383 |   564 |    565 |    5512 |
| UD_Dutch-Alpino        |   12269 |   718 |    596 |   13583 |
| UD_Dutch-LassySmall    |    5789 |   676 |    876 |    7341 |
| UD_English-ESL         |    4124 |   500 |    500 |    5124 |
| UD_English-EWT         |   12543 |  2002 |   2077 |   16622 |
| UD_English-GUM         |    2914 |   707 |    778 |    4399 |
| UD_English-LinES       |    2738 |   912 |    914 |    4564 |
| UD_English-ParTUT      |    1781 |   156 |    153 |    2090 |
| UD_Estonian-EDT        |   24384 |  3125 |   3214 |   30723 |
| UD_Finnish-FTB         |   14981 |  1875 |   1867 |   18723 |
| UD_Finnish-TDT         |   12217 |  1364 |   1555 |   15136 |
| UD_French-GSD          |   14449 |  1476 |    416 |   16341 |
| UD_French-ParTUT       |     803 |   107 |    110 |    1020 |
| UD_French-Sequoia      |    2231 |   412 |    456 |    3099 |
| UD_Galician-CTG        |    2272 |   860 |    861 |    3993 |
| UD_Galician-TreeGal    |     600 |     0 |    400 |    1000 |
| UD_German-GSD          |   13814 |   799 |    977 |   15590 |
| UD_Greek-GDT           |    1662 |   403 |    456 |    2521 |
| UD_Hebrew-HTB          |    5241 |   484 |    491 |    6216 |
| UD_Hindi-HDTB          |   13304 |  1659 |   1684 |   16647 |
| UD_Hungarian-Szeged    |     910 |   441 |    449 |    1800 |
| UD_Indonesian-GSD      |    4477 |   559 |    557 |    5593 |
| UD_Irish-IDT           |     566 |     0 |    454 |    1020 |
| UD_Italian-ISDT        |   13121 |   564 |    482 |   14167 |
| UD_Italian-ParTUT      |    1781 |   156 |    153 |    2090 |
| UD_Japanese-GSD        |    7133 |   511 |    551 |    8195 |
| UD_Kazakh-KTB          |      31 |     0 |   1047 |    1078 |
| UD_Korean-GSD          |    4400 |   950 |    989 |    6339 |
| UD_Korean-Kaist        |   23010 |  2066 |   2287 |   27363 |
| UD_Latin-ITTB          |   16809 |  2101 |   2101 |   21011 |
| UD_Latin-PROIEL        |   15906 |  1234 |   1260 |   18400 |
| UD_Latin-Perseus       |    1334 |     0 |    939 |    2273 |
| UD_Latvian-LVTB        |    7163 |  1304 |   1453 |    9920 |
| UD_Lithuanian-HSE      |     153 |    55 |     55 |     263 |
| UD_Marathi-UFAL        |     373 |    46 |     47 |     466 |
| UD_Norwegian-Bokmaal   |   15696 |  2410 |   1939 |   20045 |
| UD_Norwegian-Nynorsk   |   14174 |  1890 |   1511 |   17575 |
| UD_Persian-Seraji      |    4798 |   599 |    600 |    5997 |
| UD_Polish-LFG          |   13774 |  1745 |   1727 |   17246 |
| UD_Polish-SZ           |    6100 |  1027 |   1100 |    8227 |
| UD_Portuguese-Bosque   |    8328 |   560 |    477 |    9365 |
| UD_Portuguese-GSD      |    9664 |  1210 |   1204 |   12078 |
| UD_Romanian-RRT        |    8043 |   752 |    729 |    9524 |
| UD_Russian-GSD         |    3850 |   579 |    601 |    5030 |
| UD_Russian-SynTagRus   |   48814 |  6584 |   6491 |   61889 |
| UD_Serbian-SET         |    2935 |   465 |    491 |    3891 |
| UD_Slovak-SNK          |    8483 |  1060 |   1061 |   10604 |
| UD_Slovenian-SSJ       |    6478 |   734 |    788 |    8000 |
| UD_Spanish-AnCora      |   14305 |  1654 |   1721 |   17680 |
| UD_Spanish-GSD         |   14187 |  1400 |    426 |   16013 |
| UD_Swedish-LinES       |    2738 |   912 |    914 |    4564 |
| UD_Swedish-Talbanken   |    4303 |   504 |   1219 |    6026 |
| UD_Tamil-TTB           |     400 |    80 |    120 |     600 |
| UD_Telugu-MTG          |    1051 |   131 |    146 |    1328 |
| UD_Turkish-IMST        |    3685 |   975 |    975 |    5635 |
| UD_Urdu-UDTB           |    4043 |   552 |    535 |    5130 |
| UD_Vietnamese-VTB      |    1400 |   800 |    800 |    3000 |






The model is evaluated on the Stanford Dependency conversion ([v3.3.0](https://nlp.stanford.edu/software/stanford-parser-full-2013-11-12.zip)) of the English Penn Treebank with POS tags predicted by [Stanford POS tagger](https://nlp.stanford.edu/software/stanford-postagger-full-2018-10-16.zip).

For all datasets, we follow the conventional data splits:

* Train: 02-21 (39,832 sentences)
* Dev: 22 (1,700 sentences)
* Test: 23 (2,416 sentences)

## Performance

|               |  UAS  |  LAS  |
| ------------- | :---: | :---: |
| tag embedding | 95.87 | 94.19 |
| char lstm     | 96.17 | 94.53 |

Note that punctuation is excluded in all evaluation metrics. 

Aside from using consistent hyperparameters, there are some keypoints that significantly affect the performance:

- Dividing the pretrained embedding by its standard-deviation
- Applying the same dropout mask at every recurrent timestep
- Jointly dropping the words and tags

For the above reasons, we may have to give up some native modules in pytorch (e.g., `LSTM` and `Dropout`), and use self-implemented ones instead.

As shown above, our results, especially on char lstm version, have outperformed the [offical implementation](https://github.com/tdozat/Parser-v1) (95.74 and 94.08).

## Usage

You can start the training, evaluation and prediction process by using subcommands registered in `parser.commands`.

```sh
$ python run.py -h
usage: run.py [-h] {evaluate,predict,train} ...

Create the Biaffine Parser model.

optional arguments:
  -h, --help            show this help message and exit

Commands:
  {evaluate,predict,train}
    evaluate            Evaluate the specified model and dataset.
    predict             Use a trained model to make predictions.
    train               Train a model.
```

Before triggering the subparser, please make sure that the data files must be in CoNLL-X format. If some fields are missing, you can use underscores as placeholders.

Optional arguments of the subparsers are as follows:

```sh
$ python run.py train -h
usage: run.py train [-h] [--ftrain FTRAIN] [--fdev FDEV] [--ftest FTEST]
                    [--fembed FEMBED] [--device DEVICE] [--seed SEED]
                    [--threads THREADS] [--file FILE] [--vocab VOCAB]

optional arguments:
  -h, --help            show this help message and exit
  --ftrain FTRAIN       path to train file
  --fdev FDEV           path to dev file
  --ftest FTEST         path to test file
  --fembed FEMBED       path to pretrained embedding file
  --device DEVICE, -d DEVICE
                        ID of GPU to use
  --seed SEED, -s SEED  seed for generating random numbers
  --threads THREADS, -t THREADS
                        max num of threads
  --file FILE, -f FILE  path to model file
  --vocab VOCAB, -v VOCAB
                        path to vocabulary file

$ python run.py evaluate -h
usage: run.py evaluate [-h] [--batch-size BATCH_SIZE] [--include-punct]
                       [--fdata FDATA] [--device DEVICE] [--seed SEED]
                       [--threads THREADS] [--file FILE] [--vocab VOCAB]

optional arguments:
  -h, --help            show this help message and exit
  --batch-size BATCH_SIZE
                        batch size
  --include-punct       whether to include punctuation
  --fdata FDATA         path to dataset
  --device DEVICE, -d DEVICE
                        ID of GPU to use
  --seed SEED, -s SEED  seed for generating random numbers
  --threads THREADS, -t THREADS
                        max num of threads
  --file FILE, -f FILE  path to model file
  --vocab VOCAB, -v VOCAB
                        path to vocabulary file

$ python run.py predict -h
usage: run.py predict [-h] [--batch-size BATCH_SIZE] [--fdata FDATA]
                      [--fpred FPRED] [--device DEVICE] [--seed SEED]
                      [--threads THREADS] [--file FILE] [--vocab VOCAB]

optional arguments:
  -h, --help            show this help message and exit
  --batch-size BATCH_SIZE
                        batch size
  --fdata FDATA         path to dataset
  --fpred FPRED         path to predicted result
  --device DEVICE, -d DEVICE
                        ID of GPU to use
  --seed SEED, -s SEED  seed for generating random numbers
  --threads THREADS, -t THREADS
                        max num of threads
  --file FILE, -f FILE  path to model file
  --vocab VOCAB, -v VOCAB
                        path to vocabulary file
```

## Hyperparameters

| Param         | Description                             |                                 Value                                  |
| :------------ | :-------------------------------------- | :--------------------------------------------------------------------: |
| n_embed       | dimension of word embedding             |                                  100                                   |
| n_tag_embed   | dimension of tag embedding              |                                  100                                   |
| embed_dropout | dropout ratio of embeddings             |                                  0.33                                  |
| n_lstm_hidden | dimension of lstm hidden state          |                                  400                                   |
| n_lstm_layers | number of lstm layers                   |                                   3                                    |
| lstm_dropout  | dropout ratio of lstm                   |                                  0.33                                  |
| n_mlp_arc     | arc mlp size                            |                                  500                                   |
| n_mlp_rel     | label mlp size                          |                                  100                                   |
| mlp_dropout   | dropout ratio of mlp                    |                                  0.33                                  |
| lr            | starting learning rate of training      |                                  2e-3                                  |
| betas         | hyperparameter of momentum and L2 norm  |                               (0.9, 0.9)                               |
| epsilon       | stability constant                      |                                 1e-12                                  |
| annealing     | formula of learning rate annealing      | <img src="https://latex.codecogs.com/gif.latex?.75^{\frac{t}{5000}}"/> |
| batch_size    | number of sentences per training update |                                  200                                   |
| epochs        | max number of epochs                    |                                  1000                                  |
| patience      | patience for early stop                 |                                  100                                   |

## References

* [Deep Biaffine Attention for Neural Dependency Parsing](https://arxiv.org/abs/1611.01734)
 