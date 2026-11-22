// SlimBook Content Filter v1.1
// Removes ads, stories, suggestions, group/page suggestions from web.facebook.com
(function() {
    var highlightMode = window.__slimbook_highlight || false;

    // ------------------------------------------------------------------
    // LOCALISED LABELS
    //
    // Facebook renders its UI in the account's language, so every label the
    // filter keys on needs listing per language. Matching is exact, but
    // case-, accent- and punctuation-insensitive (see norm()), so "Takip Et",
    // "Takip et" and "· Takip Et" are one entry.
    //
    // To add or fix a language: turn on Highlight mode, find what is not
    // being caught, and add the exact text Facebook shows to the right
    // category below. Unknown categories can simply be left out.
    //
    //   ad       - the "Sponsored" marker on ads
    //   story    - the "Create story" tile (anchors the stories row)
    //   join     - "Join" button on posts from groups you are not in
    //   follow   - "Follow" button on posts from pages you do not follow
    //   friend   - "Add friend" button on posts from non-friends
    //   suggest  - headings of suggestion blocks
    //   interest - the "Are you interested in this post?" prompt
    //   app      - the "Open app" banner
    // ------------------------------------------------------------------
    var LABELS = {
        en: { ad: ['Sponsored', 'Ad'], story: ['Create story'], join: ['Join'], follow: ['Follow'],
              friend: ['Add friend'], suggest: ['People you may know', 'Suggested for you'],
              interest: ['Are you interested in this post?'], app: ['Open app'] },
        tr: { ad: ['Sponsorlu', 'Reklam'], story: ['Hikaye oluştur', 'Hikayeni oluştur'],
              join: ['Katıl', 'Gruba katıl'], follow: ['Takip et'],
              friend: ['Arkadaş ekle', 'Arkadaşı ekle', 'Arkadaşlık isteği gönder'],
              suggest: ['Tanıyor olabileceğin kişiler', 'Senin için önerilenler', 'Sana önerilenler', 'Sizin için önerilenler'],
              interest: ['Bu gönderi ilgini çekiyor mu?', 'Bu gönderiyle ilgileniyor musun?'],
              app: ['Uygulamayı aç', 'Uygulamada aç', 'Facebook uygulamasını aç'] },
        de: { ad: ['Gesponsert', 'Anzeige'], story: ['Story erstellen'], join: ['Beitreten'],
              follow: ['Folgen', 'Abonnieren'],
              friend: ['Freund/in hinzufügen', 'Als Freund/in hinzufügen', 'Freund hinzufügen'],
              suggest: ['Personen, die du kennen könntest', 'Personen, die du vielleicht kennst', 'Vorschläge für dich', 'Für dich vorgeschlagen'],
              app: ['App öffnen'] },
        fr: { ad: ['Sponsorisé', 'Publicité'], story: ['Créer une story'], join: ['Rejoindre', 'Rejoindre le groupe'],
              follow: ['Suivre', "S'abonner"],
              friend: ['Ajouter un(e) ami(e)', 'Ajouter en ami', 'Ajouter un ami'],
              suggest: ['Vous connaissez peut-être', 'Personnes que vous pourriez connaître', 'Suggestions pour vous', 'Suggéré pour vous'],
              app: ["Ouvrir l'application", "Ouvrir l'app"] },
        es: { ad: ['Publicidad', 'Patrocinado'], story: ['Crear historia', 'Crear una historia'],
              join: ['Unirte', 'Unirme', 'Unirse', 'Unirte al grupo'], follow: ['Seguir'],
              friend: ['Agregar a amigos', 'Añadir a amigos', 'Agregar amigo', 'Añadir amigo'],
              suggest: ['Personas que quizá conozcas', 'Personas que quizás conozcas', 'Sugerencias para ti', 'Sugerido para ti'],
              app: ['Abrir app', 'Abrir aplicación', 'Abrir la app'] },
        pt: { ad: ['Patrocinado', 'Anúncio'], story: ['Criar story', 'Criar história'],
              join: ['Participar', 'Aderir', 'Participar do grupo', 'Aderir ao grupo'], follow: ['Seguir'],
              friend: ['Adicionar amigo', 'Adicionar aos amigos', 'Adicionar amigo(a)'],
              suggest: ['Pessoas que você talvez conheça', 'Pessoas que talvez conheças', 'Sugestões para você', 'Sugestões para ti', 'Sugerido para você'],
              app: ['Abrir app', 'Abrir aplicativo', 'Abrir aplicação'] },
        it: { ad: ['Sponsorizzato', 'Sponsorizzata', 'Inserzione'], story: ['Crea storia', 'Crea una storia'],
              join: ['Iscriviti', 'Unisciti', 'Iscriviti al gruppo'], follow: ['Segui'],
              friend: ['Aggiungi agli amici', 'Aggiungi amico'],
              suggest: ['Persone che potresti conoscere', 'Suggeriti per te', 'Consigliati per te'],
              app: ["Apri l'app", 'Apri app'] },
        nl: { ad: ['Gesponsord', 'Advertentie'], story: ['Verhaal maken'], join: ['Lid worden', 'Deelnemen'],
              follow: ['Volgen'], friend: ['Vriend toevoegen', 'Toevoegen als vriend'],
              suggest: ['Mensen die je misschien kent', 'Voorgesteld voor jou', 'Suggesties voor jou'],
              app: ['App openen'] },
        pl: { ad: ['Sponsorowane', 'Reklama'], story: ['Utwórz relację'], join: ['Dołącz', 'Dołącz do grupy'],
              follow: ['Obserwuj'], friend: ['Dodaj znajomego', 'Dodaj do znajomych'],
              suggest: ['Osoby, które możesz znać', 'Proponowane dla Ciebie', 'Sugerowane dla Ciebie'],
              app: ['Otwórz aplikację'] },
        ru: { ad: ['Реклама'], story: ['Создать историю'], join: ['Вступить', 'Присоединиться', 'Вступить в группу'],
              follow: ['Подписаться'], friend: ['Добавить в друзья', 'Добавить друга'],
              suggest: ['Вы можете их знать', 'Люди, которых вы можете знать', 'Рекомендации для вас', 'Рекомендуемое для вас'],
              app: ['Открыть приложение'] },
        uk: { ad: ['Реклама', 'Спонсоровано'], story: ['Створити історію'], join: ['Приєднатися'],
              follow: ['Стежити', 'Підписатися'], friend: ['Додати до друзів', 'Додати друга'],
              suggest: ['Люди, яких ви можете знати', 'Рекомендовано для вас'],
              app: ['Відкрити додаток', 'Відкрити застосунок'] },
        bg: { ad: ['Спонсорирано', 'Реклама'], join: ['Присъединяване'], follow: ['Следване', 'Последване'],
              friend: ['Добавяне на приятел'], suggest: ['Хора, които може би познавате'] },
        cs: { ad: ['Sponzorováno', 'Reklama'], story: ['Vytvořit příběh'], join: ['Přidat se'],
              follow: ['Sledovat'], friend: ['Přidat přítele', 'Přidat do přátel'],
              suggest: ['Koho možná znáte', 'Lidé, které možná znáte', 'Navrhované pro vás'],
              app: ['Otevřít aplikaci'] },
        sk: { ad: ['Sponzorované', 'Reklama'], join: ['Pridať sa'], follow: ['Sledovať'],
              friend: ['Pridať priateľa'], app: ['Otvoriť aplikáciu'] },
        hr: { ad: ['Sponzorirano', 'Oglas'], join: ['Pridruži se'], follow: ['Prati', 'Zaprati'],
              friend: ['Dodaj prijatelja'], suggest: ['Osobe koje možda poznaješ', 'Osobe koje možda poznajete'] },
        sr: { ad: ['Спонзорисано', 'Sponzorisano', 'Оглас'], join: ['Придружи се'], follow: ['Прати', 'Запрати'],
              friend: ['Додај пријатеља'], suggest: ['Особе које можда познајете'] },
        ro: { ad: ['Sponsorizat', 'Reclamă'], story: ['Creează o poveste', 'Creează poveste'],
              join: ['Înscrie-te', 'Alătură-te'], follow: ['Urmărește'],
              friend: ['Adaugă prieten', 'Adaugă ca prieten'],
              suggest: ['Persoane pe care s-ar putea să le cunoști', 'Sugestii pentru tine'],
              app: ['Deschide aplicația'] },
        hu: { ad: ['Hirdetés', 'Szponzorált'], story: ['Történet létrehozása'], join: ['Csatlakozás'],
              follow: ['Követés'], friend: ['Ismerősnek jelölés'],
              suggest: ['Ismerhetsz embereket', 'Emberek, akiket ismerhetsz', 'Neked javasolt'],
              app: ['Alkalmazás megnyitása'] },
        el: { ad: ['Χορηγούμενη', 'Χορηγούμενο', 'Διαφήμιση'], story: ['Δημιουργία ιστορίας'],
              join: ['Συμμετοχή', 'Γίνετε μέλος'], follow: ['Ακολουθήστε', 'Ακολούθηση'],
              friend: ['Προσθήκη φίλου'], suggest: ['Άτομα που ίσως γνωρίζετε', 'Προτείνεται για εσάς'],
              app: ['Άνοιγμα εφαρμογής'] },
        sv: { ad: ['Sponsrad', 'Annons'], story: ['Skapa händelse'], join: ['Gå med'], follow: ['Följ'],
              friend: ['Lägg till vän', 'Lägg till som vän'],
              suggest: ['Personer du kanske känner', 'Föreslås för dig', 'Förslag för dig'],
              app: ['Öppna appen'] },
        da: { ad: ['Sponsoreret', 'Annonce'], story: ['Opret historie'], join: ['Bliv medlem', 'Deltag'],
              follow: ['Følg'], friend: ['Tilføj ven'],
              suggest: ['Personer, du måske kender', 'Foreslået til dig'], app: ['Åbn app', 'Åbn appen'] },
        nb: { ad: ['Sponset', 'Annonse'], story: ['Opprett historie', 'Lag historie'], join: ['Bli med', 'Bli medlem'],
              follow: ['Følg'], friend: ['Legg til venn'],
              suggest: ['Personer du kanskje kjenner', 'Foreslått for deg'], app: ['Åpne appen'] },
        fi: { ad: ['Sponsoroitu', 'Mainos'], story: ['Luo tarina'], join: ['Liity', 'Liity ryhmään'],
              follow: ['Seuraa'], friend: ['Lisää kaveri', 'Lisää kaveriksi'],
              suggest: ['Ihmisiä, jotka saatat tuntea', 'Sinulle ehdotettua'], app: ['Avaa sovellus'] },
        ar: { ad: ['ممول', 'إعلان'], story: ['إنشاء قصة'], join: ['انضمام', 'انضمام إلى المجموعة'],
              follow: ['متابعة'], friend: ['إضافة صديق'],
              suggest: ['أشخاص قد تعرفهم', 'مقترح لك', 'مقترحة لك'], app: ['فتح التطبيق'] },
        he: { ad: ['ממומן', 'מודעה'], story: ['יצירת סטורי'], join: ['הצטרף', 'הצטרפי', 'הצטרפות'],
              follow: ['עקוב', 'עקבי', 'מעקב'], friend: ['הוספת חבר', 'הוסף חבר/ה', 'הוסף לחברים'],
              suggest: ['אנשים שאולי אתה מכיר', 'אנשים שאולי את/ה מכיר/ה', 'הצעות בשבילך'],
              app: ['פתח אפליקציה', 'פתיחת האפליקציה'] },
        fa: { ad: ['دارای پشتیبانی مالی', 'تبلیغ', 'تبلیغات'], join: ['پیوستن'], follow: ['دنبال کردن'],
              friend: ['افزودن دوست'], suggest: ['افرادی که شاید بشناسید'] },
        hi: { ad: ['प्रायोजित', 'विज्ञापन'], story: ['स्टोरी बनाएं'], join: ['जुड़ें', 'शामिल हों'],
              follow: ['फ़ॉलो करें'], friend: ['मित्र जोड़ें', 'फ़्रेंड जोड़ें', 'मित्र बनाएं'],
              suggest: ['वे लोग जिन्हें आप शायद जानते हों', 'आपके लिए सुझाव'], app: ['ऐप खोलें'] },
        bn: { ad: ['স্পনসর্ড', 'স্পনসর করা'], join: ['যোগ দিন'], follow: ['ফলো করুন'], friend: ['বন্ধু যোগ করুন'] },
        id: { ad: ['Bersponsor', 'Disponsori', 'Iklan'], story: ['Buat cerita'],
              join: ['Gabung', 'Bergabung', 'Gabung ke grup'], follow: ['Ikuti'],
              friend: ['Tambah teman', 'Tambahkan teman'],
              suggest: ['Orang yang mungkin Anda kenal', 'Disarankan untuk Anda', 'Saran untuk Anda'],
              app: ['Buka aplikasi'] },
        ms: { ad: ['Ditaja', 'Iklan'], join: ['Sertai'], follow: ['Ikuti'], friend: ['Tambah rakan'],
              suggest: ['Orang yang mungkin anda kenali', 'Dicadangkan untuk anda'], app: ['Buka aplikasi'] },
        vi: { ad: ['Được tài trợ', 'Quảng cáo'], story: ['Tạo tin'], join: ['Tham gia', 'Tham gia nhóm'],
              follow: ['Theo dõi'], friend: ['Thêm bạn bè', 'Thêm bạn', 'Kết bạn'],
              suggest: ['Những người bạn có thể biết', 'Gợi ý cho bạn'], app: ['Mở ứng dụng'] },
        th: { ad: ['ได้รับการสนับสนุน', 'โฆษณา'], story: ['สร้างสตอรี่'], join: ['เข้าร่วม', 'เข้าร่วมกลุ่ม'],
              follow: ['ติดตาม'], friend: ['เพิ่มเพื่อน', 'เพิ่มเป็นเพื่อน'],
              suggest: ['คนที่คุณอาจรู้จัก', 'แนะนำสำหรับคุณ'], app: ['เปิดแอพ', 'เปิดแอป'] },
        ja: { ad: ['広告', 'スポンサー'], story: ['ストーリーズを作成'], join: ['参加する', 'グループに参加'],
              follow: ['フォロー', 'フォローする'], friend: ['友達になる', '友達を追加'],
              suggest: ['知り合いかも', 'あなたへのおすすめ'], app: ['アプリを開く'] },
        ko: { ad: ['광고', '스폰서'], story: ['스토리 만들기'], join: ['가입하기', '그룹 가입'],
              follow: ['팔로우'], friend: ['친구 추가'],
              suggest: ['알 수도 있는 사람', '회원님을 위한 추천'], app: ['앱 열기'] },
        zh: { ad: ['赞助内容', '赞助', '贊助', '广告', '廣告'], story: ['创建快拍', '建立限時動態'],
              join: ['加入小组', '加入社團', '加入'], follow: ['关注', '追蹤'],
              friend: ['加为好友', '加為好友', '加朋友', '添加好友'],
              suggest: ['你可能认识的人', '你可能認識的人', '你可能認識的朋友', '为你推荐', '為你推薦'],
              app: ['打开应用', '開啟應用程式'] }
    };

    // WebLite appends private-use icon-font glyphs to labels, e.g. the
    // "Sponsored" label's textContent is actually "Sponsored" + U+F078B/U+F17E1.
    var PUA = /[\uE000-\uF8FF]|[\u{F0000}-\u{FFFFD}]|[\u{100000}-\u{10FFFD}]/gu;

    // Fold a label to a comparable form: no icon glyphs, lower case, no
    // accents/diacritics, straight apostrophes, single spaces, and no
    // leading/trailing "·" separators. Turkish needs two extra steps:
    // "İ" lowercases to "i" + U+0307 (removed with the other marks) and the
    // dotless "ı" has no decomposition, so it is mapped to "i" by hand.
    function norm(s) {
        return (s || '').replace(PUA, '').toLowerCase().normalize('NFD')
            .replace(/\p{M}/gu, '').replace(/\u0131/g, 'i')
            .replace(/[\u2018\u2019\u02BC]/g, "'").replace(/\s+/g, ' ')
            .replace(/^[\s\u00B7\u2022]+|[\s\u00B7\u2022]+$/g, '');
    }

    // normalised label -> category, across every language. The page language
    // is deliberately not used to narrow this: the matches are exact, so
    // cross-language collisions are not a practical concern, and it keeps
    // working if Facebook mislabels the page language or mixes languages.
    var TERMS = {};
    var AD_LONG = [];
    (function buildTerms() {
        for (var lang in LABELS) {
            for (var cat in LABELS[lang]) {
                var list = LABELS[lang][cat];
                for (var i = 0; i < list.length; i++) {
                    var n = norm(list[i]);
                    if (!n || TERMS[n]) continue;
                    TERMS[n] = cat;
                    // Only distinctive ad words are safe to look for *inside* a longer aria-label
                    if (cat === 'ad' && n.length >= 7) AD_LONG.push(n.replace(/[.*+?^${}()|[\]\\\/]/g, '\\$&'));
                }
            }
        }
    })();
    var AD_IN_LABEL = new RegExp('(?<![\\p{L}\\p{N}])(?:' + AD_LONG.join('|') + ')(?![\\p{L}\\p{N}])', 'u');
    // English "Ad" as a whole word ("Ad", "Ad · Shop now") but not Adam, Adnan, Add, Ads
    var AD_WORD = /(?<![\p{L}\p{N}])Ad(?![\p{L}\p{N}])/u;

    function pageLang() {
        var l = (document.documentElement && document.documentElement.lang) || '';
        return l.toLowerCase().split(/[-_]/)[0];
    }

    // Which kind of marker is this piece of label text, if any?
    function classify(text) {
        var stripped = (text || '').replace(PUA, '').trim();
        if (!stripped || stripped.length > 80) return null;
        var n = norm(stripped);
        if (TERMS[n]) return TERMS[n];
        // "Sponsored · Paid for by X"
        if (n.length < 60 && n.indexOf('\u00B7') !== -1) {
            var parts = n.split('\u00B7');
            for (var i = 0; i < parts.length; i++) {
                if (TERMS[parts[i].trim()] === 'ad') return 'ad';
            }
        }
        // Loose English rule, kept from before multi-language support. Only
        // on English (or unlabelled) pages: "Ad" is an ordinary word
        // elsewhere, e.g. "name" in Turkish.
        var lang = pageLang();
        if ((!lang || lang === 'en') && stripped.length < 20 && AD_WORD.test(stripped)) return 'ad';
        return null;
    }

    function hide(el, type) {
        if (!el || el.getAttribute('data-filtered')) return;
        // Log what's being filtered
        var preview = (el.innerText || '').substring(0, 80).replace(/\n/g, ' ');
        console.log('SLIMBOOK_HIDE:' + type + ':hl=' + highlightMode + ':' + preview);
        if (highlightMode) {
            el.style.outline = '3px solid red';
            el.style.opacity = '0.4';
            el.style.position = 'relative';
            var label = document.createElement('div');
            label.textContent = type.toUpperCase();
            label.style.cssText = 'position:absolute;top:0;left:0;background:red;color:white;font-size:11px;padding:2px 6px;z-index:9999;font-weight:bold;border-radius:0 0 4px 0;';
            el.appendChild(label);
        } else {
            el.style.setProperty('display', 'none', 'important');
            el.setAttribute('hidden', '');
            // Collapse parent wrappers that would leave grey gaps
            var parent = el.parentElement;
            for (var i = 0; i < 5; i++) {
                if (!parent) break;
                // If parent has no other visible children, collapse it too
                var hasVisibleSibling = false;
                for (var j = 0; j < parent.children.length; j++) {
                    var sib = parent.children[j];
                    if (sib !== el && !sib.getAttribute('data-filtered') &&
                        !sib.hasAttribute('hidden') &&
                        sib.offsetHeight > 0) {
                        hasVisibleSibling = true;
                        break;
                    }
                }
                if (!hasVisibleSibling) {
                    parent.style.setProperty('display', 'none', 'important');
                    parent.setAttribute('hidden', '');
                    parent = parent.parentElement;
                } else {
                    break;
                }
            }
        }
        el.setAttribute('data-filtered', type);
    }

    // Walk up to find a sizeable parent container
    function findContainer(el, minH, maxH) {
        var parent = el;
        for (var i = 0; i < 25; i++) {
            if (!parent.parentElement) break;
            parent = parent.parentElement;
            var h = parent.getBoundingClientRect().height;
            if (h >= minH && h <= maxH) {
                return parent;
            }
        }
        return null;
    }

    // Parse post timestamp and return age in hours
    function getPostAgeHours(container) {
        // Find timestamp text: look for small spans near the top of the post
        var spans = container.querySelectorAll('span');
        var timeText = '';
        for (var i = 0; i < spans.length && i < 25; i++) {
            var st = spans[i].textContent || '';
            // Timestamp patterns: short text with relative time or date
            if (st.length > 1 && st.length < 60 &&
                (st.match(/\d+[hdmw]/) || st.indexOf('Yesterday') !== -1 ||
                 st.match(/\d{1,2}\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)/i) ||
                 st.match(/(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2}/i))) {
                timeText = st;
                break;
            }
        }
        if (!timeText) {
            return -1;
        }

        // Relative: Xh (check before Xm to avoid month confusion)
        var hourMatch = timeText.match(/(\d+)h/);
        if (hourMatch) return parseInt(hourMatch[1]);
        // Relative: Xd
        var dayMatch = timeText.match(/(\d+)d/);
        if (dayMatch) return parseInt(dayMatch[1]) * 24;
        // Relative: Xw
        var weekMatch = timeText.match(/(\d+)w/);
        if (weekMatch) return parseInt(weekMatch[1]) * 168;
        // Relative: Xm (but not month names)
        var minMatch = timeText.match(/(\d+)m/);
        if (minMatch && !timeText.match(/\d+\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)/i)) {
            return parseInt(minMatch[1]) / 60;
        }
        // Yesterday
        if (timeText.indexOf('Yesterday') !== -1) return 24;
        // Absolute: "5 Jun", "13 June", "Jun 9", "June 13 at 10:27"
        var months = {jan:0,feb:1,mar:2,apr:3,may:4,jun:5,jul:6,aug:7,sep:8,oct:9,nov:10,dec:11,
                      january:0,february:1,march:2,april:3,june:5,july:6,august:7,september:8,october:9,november:10,december:11};
        var absMatch = timeText.match(/(\d{1,2})\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)(?:\s+(\d{4}))?/i);
        if (!absMatch) {
            // Try month-first: "Jun 9", "June 13"
            var mfMatch = timeText.match(/(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\s+(\d{1,2})(?:\s+(\d{4}))?/i);
            if (mfMatch) {
                absMatch = [null, mfMatch[2], mfMatch[1], mfMatch[3]];
            }
        }
        if (absMatch) {
            var day = parseInt(absMatch[1]);
            var mon = months[absMatch[2].toLowerCase()];
            var year = absMatch[3] ? parseInt(absMatch[3]) : new Date().getFullYear();
            var postDate = new Date(year, mon, day);
            var now = new Date();
            var diff = now - postDate;
            if (diff < 0) diff = 0;
            return diff / (1000 * 60 * 60);
        }
        return -1;
    }

    // Determine if a container is a suggestion card vs a real post.
    // Real posts have Like/Comment/Share interaction buttons.
    // Suggestions are simple cards with just an action button and no engagement row.
    function isSuggestionCard(container) {
        var text = container.innerText || '';
        // Real posts have engagement actions
        var hasLike = text.indexOf('Like') !== -1 && text.indexOf('Comment') !== -1;
        if (hasLike) return false;
        // Real posts have reaction/comment/share icons (spans with single PUA chars near bottom)
        var ariaLabels = container.querySelectorAll('[aria-label]');
        for (var i = 0; i < ariaLabels.length; i++) {
            var label = ariaLabels[i].getAttribute('aria-label') || '';
            if (label.indexOf('Like') !== -1 || label.indexOf('Comment') !== -1 ||
                label.indexOf('Share') !== -1 || label.indexOf('React') !== -1) {
                return false;
            }
        }
        // No engagement row found — it's a suggestion
        return true;
    }

    function removeUnwanted() {
        // Always read from window in case script was re-injected
        highlightMode = window.__slimbook_highlight || false;

        // Only filter on the home feed
        var path = window.location.pathname;
        if (path !== '/' && path !== '/home.php' && path !== '/index.php') return;

        // Scan all elements that could contain marker text
        var els = document.querySelectorAll('[data-mcomponent="TextArea"], [data-mcomponent="ServerTextArea"]');
        // If MComponent attributes not present, fall back to links and spans
        if (els.length === 0) {
            els = document.querySelectorAll('a, span[role="link"], [role="button"]');
        }

        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            if (el.closest('[data-filtered]')) continue;
            var text = el.textContent || '';
            // No marker label is anywhere near this long; skip post bodies cheaply
            if (text.length > 120) continue;
            // Strip icon-font glyphs so the comparisons below still work.
            var trimmed = text.replace(PUA, '').trim();
            var kind = classify(text);

            // ADS: the "Sponsored" marker, in any supported language
            if (kind === 'ad') {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'ad');
                continue;
            }

            // STORIES
            if (kind === 'story') {
                var p = el;
                var found = false;
                for (var k = 0; k < 15; k++) {
                    if (!p.parentElement) break;
                    p = p.parentElement;
                    var pStyle = p.getAttribute('style') || '';
                    if (pStyle.indexOf('height') !== -1 && p.getBoundingClientRect().height > 100 &&
                        p.getBoundingClientRect().height < 500) {
                        found = true;
                        break;
                    }
                }
                if (found) hide(p, 'stories');
                continue;
            }

            // REELS
            if ((trimmed === 'Reels' || trimmed.indexOf('Reels') !== -1) && trimmed.length < 30) {
                var p = el;
                var found = false;
                for (var k = 0; k < 25; k++) {
                    if (!p.parentElement) break;
                    p = p.parentElement;
                    var ph = p.getBoundingClientRect().height;
                    if (ph > 400 && ph < window.innerHeight * 2 && p.children.length >= 2) {
                        found = true;
                        break;
                    }
                }
                if (found) hide(p, 'reels');
                continue;
            }

            // GROUP SUGGESTIONS / posts from unjoined groups
            if (kind === 'join') {
                var container = findContainer(el, 200, 4000);
                if (container) {
                    var elTop = el.getBoundingClientRect().top;
                    var containerTop = container.getBoundingClientRect().top;
                    // Only filter if Join is in the top 60px (main header) of container
                    if (elTop - containerTop < 60) {
                        hide(container, 'group');
                    }
                }
                continue;
            }

            // PAGE SUGGESTIONS / posts from unfollowed pages
            if (kind === 'follow') {
                var container = findContainer(el, 200, 4000);
                if (container) {
                    var elTop = el.getBoundingClientRect().top;
                    var containerTop = container.getBoundingClientRect().top;
                    if (elTop - containerTop < 60) {
                        hide(container, 'page');
                    }
                }
                continue;
            }

            // NON-FRIENDS: posts from people you're not friends with
            if (kind === 'friend') {
                var container = findContainer(el, 200, 4000);
                if (container) {
                    var elTop = el.getBoundingClientRect().top;
                    var containerTop = container.getBoundingClientRect().top;
                    if (elTop - containerTop < 60) {
                        hide(container, 'nonfriend');
                    }
                }
                continue;
            }

            // SUGGESTIONS
            if (kind === 'suggest') {
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'suggestion');
                continue;
            }

            // INTEREST PROMPTS
            if (kind === 'interest') {
                var container = findContainer(el, 50, 300);
                if (container) hide(container, 'interest');
                continue;
            }

            // OPEN APP BANNER
            if (kind === 'app') {
                var container = findContainer(el, 30, 200);
                if (container) hide(container, 'banner');
                continue;
            }
        }

        // Also scan by aria-label which WebLite uses
        var labeled = document.querySelectorAll('[aria-label]');
        for (var i = 0; i < labeled.length; i++) {
            var el = labeled[i];
            var label = el.getAttribute('aria-label') || '';
            if (!label || label.length > 120) continue;
            var nLabel = norm(label);
            if (TERMS[nLabel] === 'ad' || AD_IN_LABEL.test(nLabel)) {
                if (el.closest('[data-filtered]')) continue;
                var container = findContainer(el, 200, 1500);
                if (container) hide(container, 'ad');
            }
        }

        // AUTHOR & GROUP DETECTION via Android bridge
        if (typeof Android !== 'undefined' && Android.reportAuthor) {
            var maxAgeHours = Android.getMaxAgeHours();
            var links = document.querySelectorAll('span[role="link"]');
            var processed = [];
            for (var i = 0; i < links.length; i++) {
                var link = links[i];
                if (link.closest('[data-filtered]')) continue;
                if (link.closest('[data-author-checked]')) continue;
                if (processed.indexOf(link) !== -1) continue;
                var name = link.textContent?.trim() || '';
                var lh = link.getBoundingClientRect().height;
                if (name.length < 3 || name.length > 80 || lh <= 0 || lh > 25) continue;

                // Find the post container for this link
                var postContainer = findContainer(link, 200, 4000);
                if (!postContainer || postContainer.getAttribute('data-author-checked')) continue;

                // Find all role=link spans in this post container
                var postLinks = postContainer.querySelectorAll('span[role="link"]');
                var candidates = [];
                for (var j = 0; j < postLinks.length; j++) {
                    var pl = postLinks[j];
                    var pn = pl.textContent?.trim() || '';
                    var ph = pl.getBoundingClientRect().height;
                    if (pn.length > 2 && pn.length < 80 && ph > 0 && ph < 25) {
                        candidates.push(pn);
                        processed.push(pl);
                    }
                    if (candidates.length >= 3) break;
                }

                // Determine group vs author
                // If 2+ candidates: first is likely group/page, second is author
                var groupName = '';
                var authorName = '';
                if (candidates.length >= 2) {
                    groupName = candidates[0];
                    authorName = candidates[1];
                } else if (candidates.length === 1) {
                    authorName = candidates[0];
                }

                if (authorName) {
                    postContainer.setAttribute('data-author-checked', authorName);
                    Android.reportAuthor(authorName);
                    if (groupName) Android.reportGroup(groupName);

                    if (Android.isBlocked(authorName) || (groupName && Android.isBlocked(groupName))) {
                        hide(postContainer, 'blocked');
                    } else if (maxAgeHours > 0) {
                        var ageH = getPostAgeHours(postContainer);
                        if (ageH > 0 && ageH > maxAgeHours) {
                            hide(postContainer, 'old');
                        }
                    }
                }
            }
        }

        // STANDALONE AGE FILTER: scan all visible posts that weren't age-checked yet
        if (typeof Android !== 'undefined' && Android.getMaxAgeHours) {
            var maxAge = Android.getMaxAgeHours();
            if (maxAge > 0) {
                var allChecked = document.querySelectorAll('[data-author-checked]');
                // Already handled above. Now find large containers NOT yet checked.
                var allSpans = document.querySelectorAll('span');
                var checkedContainers = [];
                for (var ci = 0; ci < allChecked.length; ci++) checkedContainers.push(allChecked[ci]);

                // Look for unchecked posts by finding timestamp spans not in checked containers
                for (var ti = 0; ti < allSpans.length && ti < 200; ti++) {
                    var sp = allSpans[ti];
                    if (sp.closest('[data-filtered]')) continue;
                    if (sp.closest('[data-author-checked]')) continue;
                    if (sp.closest('[data-age-checked]')) continue;
                    var spText = sp.textContent || '';
                    if (spText.length < 2 || spText.length > 60) continue;
                    if (spText.match(/\d+[hdw]/) || spText.match(/(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d/i) || spText.match(/\d{1,2}\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)/i)) {
                        // Found a timestamp in an unchecked post
                        var postC = findContainer(sp, 200, 4000);
                        if (postC && !postC.getAttribute('data-age-checked') && !postC.getAttribute('data-filtered')) {
                            postC.setAttribute('data-age-checked', '1');
                            var age = getPostAgeHours(postC);
                            if (age > 0 && age > maxAge) {
                                hide(postC, 'old');
                            }
                        }
                    }
                }
            }
        }

        var stats = {
            ads: document.querySelectorAll('[data-filtered="ad"]').length,
            stories: document.querySelectorAll('[data-filtered="stories"]').length,
            suggestions: document.querySelectorAll('[data-filtered="suggestion"]').length,
            groups: document.querySelectorAll('[data-filtered="group"]').length,
            pages: document.querySelectorAll('[data-filtered="page"]').length,
            blocked: document.querySelectorAll('[data-filtered="blocked"]').length,
            old: document.querySelectorAll('[data-filtered="old"]').length,
            banners: document.querySelectorAll('[data-filtered="banner"]').length
        };

        console.log('SLIMBOOK_STATS:' + JSON.stringify(stats));

        return stats;
    }

    // For not-yet-revealed content (no layout, so no geometry to go on): look
    // at the label elements that start within the first 100 characters and
    // report the first ad / unfollowed / unjoined / non-friend marker.
    var HEADER_KINDS = { ad: 'ad', follow: 'page', join: 'group', friend: 'nonfriend' };
    function headerKind(el) {
        var leaves = el.querySelectorAll('[data-mcomponent="TextArea"], [data-mcomponent="ServerTextArea"]');
        if (leaves.length === 0) leaves = el.querySelectorAll('a, span[role="link"], [role="button"]');
        var seen = 0;
        for (var i = 0; i < leaves.length && i < 20 && seen < 100; i++) {
            var t = leaves[i].textContent || '';
            var type = HEADER_KINDS[classify(t)];
            if (type) return type;
            seen += t.length;
        }
        return null;
    }

    // Pre-filter hidden content so it never flashes when revealed
    function preFilter() {
        var hidden = document.querySelectorAll('[style*="display: none"], [style*="display:none"]');
        for (var i = 0; i < hidden.length; i++) {
            var el = hidden[i];
            if (el.getAttribute('data-filtered') || el.innerHTML.length < 50) continue;
            var text = el.textContent || '';
            // Check for Follow/Join/Add friend in header-like position (first 200 chars)
            var headerText = text.replace(PUA, '').substring(0, 200);
            if (headerText.indexOf('Follow') !== -1 && headerText.indexOf('Follow') < 100) {
                hide(el, 'page');
            } else if (headerText.indexOf('Join') !== -1 && headerText.indexOf('Join') < 100) {
                hide(el, 'group');
            } else if (headerText.indexOf('Add friend') !== -1 && headerText.indexOf('Add friend') < 100) {
                hide(el, 'nonfriend');
            } else if (headerText.indexOf('Sponsored') !== -1 || AD_WORD.test(headerText) && headerText.length < 80) {
                hide(el, 'ad');
            } else {
                // Other languages: the substring checks above are too loose to
                // repeat for every language ("Folgen", "Seguir", "Katıl" are
                // ordinary words), so match whole label elements in the
                // header instead.
                var kind = headerKind(el);
                if (kind) hide(el, kind);
            }
            // Age filter: look for timestamps in the text
            if (typeof Android !== 'undefined' && Android.getMaxAgeHours) {
                var maxAge = Android.getMaxAgeHours();
                if (maxAge > 0) {
                    var dayMatch = headerText.match(/(\d+)d/);
                    var weekMatch = headerText.match(/(\d+)w/);
                    if (dayMatch && parseInt(dayMatch[1]) * 24 > maxAge) {
                        hide(el, 'old');
                    } else if (weekMatch && parseInt(weekMatch[1]) * 168 > maxAge) {
                        hide(el, 'old');
                    }
                }
            }
        }
    }

    // Run after a short delay to let WebSocket content render
    setTimeout(removeUnwanted, 1000);
    setTimeout(removeUnwanted, 3000);
    setTimeout(preFilter, 2000);
    setTimeout(preFilter, 5000);

    var observer = new MutationObserver(function() {
        setTimeout(removeUnwanted, 100);
        setTimeout(preFilter, 200);
    });
    observer.observe(document.body, { childList: true, subtree: true });

    // Composer-focus reporter: hides the native stats badge while typing.
    // Semantic (focusin/focusout) rather than IME geometry, which WebView
    // often swallows. focusout is debounced so tabbing between inputs
    // doesn't flicker the badge.
    (function hookComposerFocus() {
        if (window.__sb_composer_hooked) return;
        window.__sb_composer_hooked = true;
        function isEditable(el) {
            return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable);
        }
        document.addEventListener('focusin', function(e) {
            if (isEditable(e.target) && typeof Android !== 'undefined' && Android.setComposerFocused) {
                Android.setComposerFocused(true);
            }
        });
        document.addEventListener('focusout', function() {
            setTimeout(function() {
                if (!isEditable(document.activeElement) && typeof Android !== 'undefined' && Android.setComposerFocused) {
                    Android.setComposerFocused(false);
                }
            }, 150);
        });
    })();

    window.__slimbook_filter = removeUnwanted;

    window.__slimbook_dump = function() {
        // Dump all short text elements to understand the post structure
        var all = document.querySelectorAll('[role="link"], a, span');
        var results = [];
        for (var i = 0; i < all.length; i++) {
            var el = all[i];
            var text = el.textContent?.trim() || '';
            // Short text that could be author names or timestamps
            if (text.length > 1 && text.length < 80 && el.children.length < 3) {
                var rect = el.getBoundingClientRect();
                // Only visible elements in the top portion of a post area
                if (rect.height > 0 && rect.height < 30 && rect.top > 0) {
                    results.push({
                        text: text.substring(0, 60),
                        tag: el.tagName,
                        role: el.getAttribute('role'),
                        h: Math.round(rect.height),
                        top: Math.round(rect.top)
                    });
                }
            }
            if (results.length > 50) break;
        }
        console.log('SLIMBOOK_DUMP:' + JSON.stringify(results));
        return results;
    };
    window.__slimbook_setHighlight = function(on) {
        window.__slimbook_highlight = on;
        var filtered = document.querySelectorAll('[data-filtered]');
        for (var i = 0; i < filtered.length; i++) {
            filtered[i].removeAttribute('data-filtered');
            filtered[i].style.display = '';
            filtered[i].style.outline = '';
            filtered[i].style.opacity = '';
            filtered[i].style.position = '';
            filtered[i].removeAttribute('hidden');
        }
        // Restore any parents we collapsed
        var hidden = document.querySelectorAll('[hidden]');
        for (var i = 0; i < hidden.length; i++) {
            hidden[i].style.display = '';
            hidden[i].removeAttribute('hidden');
        }
        // Remove highlight labels
        var labels = document.querySelectorAll('[data-filtered] div, div[style*="z-index:9999"]');
        // Simpler: just re-run filter with new mode
        highlightMode = on;
        removeUnwanted();
    };
})();
