import { useMemo, useState } from "react";
import "./account.css";

export type AccountMode = "ggo" | "microsoft" | "guest";
export type SkinSource = "ggo" | "microsoft" | "default";

export type MicrosoftAccount = {
  connected: boolean;
  name: string | null;
};

export type GgoAccount = {
  connected: boolean;
  playerId: string | null;
  displayName: string | null;
  skinSource: SkinSource;
};

type Props = {
  lang: "en" | "ru" | "uk";
  nickname: string;
  onNickname: (value: string) => void;
  microsoft: MicrosoftAccount;
  ggo: GgoAccount;
  onMicrosoftLogin: () => void;
  onMicrosoftLogout: () => void;
  onGgoLogin: () => void;
  onGgoLogout: () => void;
  onGuestSelect: () => void;
  onSkinSource: (source: SkinSource) => void;
  onBack: () => void;
};

const text = {
  en: {
    eyebrow: "IDENTITY",
    title: "Accounts",
    subtitle: "One GunGloryOnline identity, multiple ways to sign in.",
    ggo: "GGO Account",
    ggoBody: "Recommended. Your GGO profile owns progression, cosmetics, social data and GGO skins.",
    microsoft: "Microsoft",
    microsoftBody: "Official Minecraft identity. Link it to GGO instead of making Minecraft your main account.",
    guest: "Quick Play",
    guestBody: "Nickname-only guest session. Fastest start, but cloud features can be limited until you create a GGO account.",
    loginGgo: "SIGN IN ON GGO WEBSITE",
    loginMicrosoft: "SIGN IN WITH MICROSOFT",
    useGuest: "USE GUEST PROFILE",
    logout: "Sign out",
    current: "Current profile",
    nickname: "Display name",
    skins: "Skin source",
    ggoSkin: "GGO skin",
    msSkin: "Official skin",
    defaultSkin: "GGO default",
    linked: "Linked",
    notLinked: "Not linked",
    future: "GGO web login activates when the account backend is deployed on ggo.kvicloud.ru.",
    back: "Back"
  },
  ru: {
    eyebrow: "АККАУНТ",
    title: "Аккаунты",
    subtitle: "Один аккаунт GunGloryOnline и несколько способов входа.",
    ggo: "GGO аккаунт",
    ggoBody: "Основной вариант. В GGO-профиле будут прогресс, косметика, социальные функции и свои скины.",
    microsoft: "Microsoft",
    microsoftBody: "Официальный Minecraft-аккаунт. Привязываем его к GGO, а не строим всю игру вокруг Minecraft.",
    guest: "Быстрый вход",
    guestBody: "Гостевой вход только по нику. Быстро, но облачные функции могут быть ограничены до регистрации GGO.",
    loginGgo: "ВОЙТИ ЧЕРЕЗ САЙТ GGO",
    loginMicrosoft: "ВОЙТИ ЧЕРЕЗ MICROSOFT",
    useGuest: "ИГРАТЬ КАК ГОСТЬ",
    logout: "Выйти",
    current: "Текущий профиль",
    nickname: "Отображаемый ник",
    skins: "Источник скина",
    ggoSkin: "Скин GGO",
    msSkin: "Официальный скин",
    defaultSkin: "Стандартный GGO",
    linked: "Привязан",
    notLinked: "Не привязан",
    future: "Вход GGO через сайт заработает после запуска account backend на ggo.kvicloud.ru.",
    back: "Назад"
  },
  uk: {
    eyebrow: "АКАУНТ",
    title: "Акаунти",
    subtitle: "Один акаунт GunGloryOnline і декілька способів входу.",
    ggo: "GGO акаунт",
    ggoBody: "Основний варіант. GGO-профіль зберігає прогрес, косметику, соціальні дані та власні скіни.",
    microsoft: "Microsoft",
    microsoftBody: "Офіційна Minecraft-ідентичність. Прив'язуємо її до GGO, а не будуємо всю гру навколо Minecraft.",
    guest: "Швидкий вхід",
    guestBody: "Гостьовий вхід лише за ніком. Швидко, але хмарні функції можуть бути обмежені до реєстрації GGO.",
    loginGgo: "УВІЙТИ ЧЕРЕЗ САЙТ GGO",
    loginMicrosoft: "УВІЙТИ ЧЕРЕЗ MICROSOFT",
    useGuest: "ГРАТИ ЯК ГІСТЬ",
    logout: "Вийти",
    current: "Поточний профіль",
    nickname: "Ім'я гравця",
    skins: "Джерело скіна",
    ggoSkin: "Скін GGO",
    msSkin: "Офіційний скін",
    defaultSkin: "Стандартний GGO",
    linked: "Прив'язано",
    notLinked: "Не прив'язано",
    future: "GGO-вхід через сайт запрацює після запуску account backend на ggo.kvicloud.ru.",
    back: "Назад"
  }
} as const;

export default function AccountHub(props: Props) {
  const t = text[props.lang];
  const [mode, setMode] = useState<AccountMode>(props.ggo.connected ? "ggo" : props.microsoft.connected ? "microsoft" : "guest");
  const profileLabel = useMemo(() => {
    if (props.ggo.connected) return props.ggo.displayName || props.nickname || "GGO Player";
    if (props.microsoft.connected) return props.microsoft.name || props.nickname || "Microsoft Player";
    return props.nickname || "Guest";
  }, [props.ggo, props.microsoft, props.nickname]);

  return <section className="page accountsPage">
    <button className="backBtn" onClick={props.onBack}>← {t.back}</button>
    <div className="accountHero">
      <div>
        <span>{t.eyebrow}</span>
        <h2>{t.title}</h2>
        <p>{t.subtitle}</p>
      </div>
      <div className="accountCurrent">
        <small>{t.current}</small>
        <strong>{profileLabel}</strong>
        <span>{props.ggo.connected ? "GGO" : props.microsoft.connected ? "MICROSOFT" : "GUEST"}</span>
      </div>
    </div>

    <div className="accountLayout">
      <div className="accountProviders">
        <button className={mode === "ggo" ? "providerCard selected" : "providerCard"} onClick={() => setMode("ggo")}>
          <div className="providerIcon ggoMark">G</div><div><strong>{t.ggo}</strong><p>{t.ggoBody}</p></div><span className={props.ggo.connected ? "providerState on" : "providerState"}>{props.ggo.connected ? t.linked : t.notLinked}</span>
        </button>
        <button className={mode === "microsoft" ? "providerCard selected" : "providerCard"} onClick={() => setMode("microsoft")}>
          <div className="providerIcon msMark"><i/><i/><i/><i/></div><div><strong>{t.microsoft}</strong><p>{t.microsoftBody}</p></div><span className={props.microsoft.connected ? "providerState on" : "providerState"}>{props.microsoft.connected ? t.linked : t.notLinked}</span>
        </button>
        <button className={mode === "guest" ? "providerCard selected" : "providerCard"} onClick={() => setMode("guest")}>
          <div className="providerIcon guestMark">↯</div><div><strong>{t.guest}</strong><p>{t.guestBody}</p></div><span className="providerState">LOCAL</span>
        </button>
      </div>

      <aside className="accountPanel">
        <small>{t.nickname}</small>
        <input className="accountName" value={props.nickname} maxLength={16} onChange={e => props.onNickname(e.target.value)} placeholder="Player name" />

        {mode === "ggo" && <div className="accountActionBlock">
          {props.ggo.connected
            ? <><div className="linkedIdentity"><b>GGO</b><div><strong>{props.ggo.displayName}</strong><span>{props.ggo.playerId}</span></div></div><button className="accountSecondary" onClick={props.onGgoLogout}>{t.logout}</button></>
            : <><button className="accountPrimary" onClick={props.onGgoLogin}>{t.loginGgo}</button><p>{t.future}</p></>}
        </div>}

        {mode === "microsoft" && <div className="accountActionBlock">
          {props.microsoft.connected
            ? <><div className="linkedIdentity"><b>MS</b><div><strong>{props.microsoft.name}</strong><span>Official Minecraft identity</span></div></div><button className="accountSecondary" onClick={props.onMicrosoftLogout}>{t.logout}</button></>
            : <button className="accountPrimary light" onClick={props.onMicrosoftLogin}>{t.loginMicrosoft}</button>}
        </div>}

        {mode === "guest" && <div className="accountActionBlock"><button className="accountPrimary ghost" onClick={props.onGuestSelect}>{t.useGuest}</button></div>}

        <div className="skinBlock">
          <small>{t.skins}</small>
          <div className="skinChoices">
            <button className={props.ggo.skinSource === "ggo" ? "active" : ""} disabled={!props.ggo.connected} onClick={() => props.onSkinSource("ggo")}><b>G</b><span>{t.ggoSkin}</span></button>
            <button className={props.ggo.skinSource === "microsoft" ? "active" : ""} disabled={!props.microsoft.connected} onClick={() => props.onSkinSource("microsoft")}><b>MS</b><span>{t.msSkin}</span></button>
            <button className={props.ggo.skinSource === "default" ? "active" : ""} onClick={() => props.onSkinSource("default")}><b>◎</b><span>{t.defaultSkin}</span></button>
          </div>
        </div>
      </aside>
    </div>
  </section>;
}
