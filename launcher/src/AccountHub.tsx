import { useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import "./account.css";

export type AccountMode = "ggo" | "microsoft" | "guest";
export type SkinSource = "ggo" | "microsoft" | "default";

export type MicrosoftAccount = {
  authenticated: boolean;
  minecraftProfile: { id:string; name:string } | null;
};

export type GgoAccount = {
  connected: boolean;
  playerId: string | null;
  displayName: string | null;
  skinSource: SkinSource;
};

type GgoAuthStatus = {
  authenticated:boolean;
  profile:null|{id:string;displayName:string;skinSource:SkinSource};
};

type Props = {
  lang: "en" | "ru" | "uk";
  nickname: string;
  setNickname: (value: string) => void;
  microsoft: MicrosoftAccount;
  ggoAccount: GgoAccount;
  setGgoAccount: (value: GgoAccount | ((current:GgoAccount)=>GgoAccount)) => void;
  apiUrl: string;
  busy: boolean;
  setBusy: (value:boolean) => void;
  setStatus: (value:string) => void;
  onMicrosoftLogin: () => void | Promise<void>;
  onMicrosoftLogout: () => void | Promise<void>;
  onLinkMinecraft: () => void | Promise<unknown>;
};

const text = {
  en: {
    eyebrow: "IDENTITY", title: "Accounts", subtitle: "One GunGloryOnline identity, multiple ways to sign in.",
    ggo: "GGO Account", ggoBody: "Recommended. Your GGO profile owns progression, cosmetics, social data and GGO skins.",
    microsoft: "Microsoft", microsoftBody: "Official Minecraft identity. Link it to GGO instead of making Minecraft your main account.",
    guest: "Quick Play", guestBody: "Nickname-only guest session. Fastest start, but cloud features can be limited until you create a GGO account.",
    loginGgo: "SIGN IN ON GGO WEBSITE", loginMicrosoft: "SIGN IN WITH MICROSOFT", useGuest: "USE GUEST PROFILE", logout: "Sign out",
    current: "Current profile", nickname: "Display name", skins: "Skin source", ggoSkin: "GGO skin", msSkin: "Official skin", defaultSkin: "GGO default",
    linked: "Linked", notLinked: "Not linked", linkMinecraft:"LINK MINECRAFT TO GGO", future: "GGO web login activates when the account backend is deployed on ggo.kvicloud.ru."
  },
  ru: {
    eyebrow: "АККАУНТ", title: "Аккаунты", subtitle: "Один аккаунт GunGloryOnline и несколько способов входа.",
    ggo: "GGO аккаунт", ggoBody: "Основной вариант. В GGO-профиле будут прогресс, косметика, социальные функции и свои скины.",
    microsoft: "Microsoft", microsoftBody: "Официальный Minecraft-аккаунт. Привязываем его к GGO, а не строим всю игру вокруг Minecraft.",
    guest: "Быстрый вход", guestBody: "Гостевой вход только по нику. Быстро, но облачные функции могут быть ограничены до регистрации GGO.",
    loginGgo: "ВОЙТИ ЧЕРЕЗ САЙТ GGO", loginMicrosoft: "ВОЙТИ ЧЕРЕЗ MICROSOFT", useGuest: "ИГРАТЬ КАК ГОСТЬ", logout: "Выйти",
    current: "Текущий профиль", nickname: "Отображаемый ник", skins: "Источник скина", ggoSkin: "Скин GGO", msSkin: "Официальный скин", defaultSkin: "Стандартный GGO",
    linked: "Привязан", notLinked: "Не привязан", linkMinecraft:"ПРИВЯЗАТЬ MINECRAFT К GGO", future: "Вход GGO через сайт заработает после запуска account backend на ggo.kvicloud.ru."
  },
  uk: {
    eyebrow: "АКАУНТ", title: "Акаунти", subtitle: "Один акаунт GunGloryOnline і декілька способів входу.",
    ggo: "GGO акаунт", ggoBody: "Основний варіант. GGO-профіль зберігає прогрес, косметику, соціальні дані та власні скіни.",
    microsoft: "Microsoft", microsoftBody: "Офіційна Minecraft-ідентичність. Прив'язуємо її до GGO, а не будуємо всю гру навколо Minecraft.",
    guest: "Швидкий вхід", guestBody: "Гостьовий вхід лише за ніком. Швидко, але хмарні функції можуть бути обмежені до реєстрації GGO.",
    loginGgo: "УВІЙТИ ЧЕРЕЗ САЙТ GGO", loginMicrosoft: "УВІЙТИ ЧЕРЕЗ MICROSOFT", useGuest: "ГРАТИ ЯК ГІСТЬ", logout: "Вийти",
    current: "Поточний профіль", nickname: "Ім'я гравця", skins: "Джерело скіна", ggoSkin: "Скін GGO", msSkin: "Офіційний скін", defaultSkin: "Стандартний GGO",
    linked: "Прив'язано", notLinked: "Не прив'язано", linkMinecraft:"ПРИВ'ЯЗАТИ MINECRAFT ДО GGO", future: "GGO-вхід через сайт запрацює після запуску account backend на ggo.kvicloud.ru."
  }
} as const;

export default function AccountHub(props: Props) {
  const t = text[props.lang];
  const microsoftConnected = props.microsoft.authenticated && Boolean(props.microsoft.minecraftProfile);
  const microsoftName = props.microsoft.minecraftProfile?.name ?? null;
  const [mode, setMode] = useState<AccountMode>(props.ggoAccount.connected ? "ggo" : microsoftConnected ? "microsoft" : "guest");
  const profileLabel = useMemo(() => {
    if (props.ggoAccount.connected) return props.ggoAccount.displayName || props.nickname || "GGO Player";
    if (microsoftConnected) return microsoftName || props.nickname || "Microsoft Player";
    return props.nickname || "Guest";
  }, [props.ggoAccount, microsoftConnected, microsoftName, props.nickname]);

  function applyGgoStatus(value:GgoAuthStatus){
    if(!value.authenticated||!value.profile){
      props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:current.skinSource==="ggo"?"default":current.skinSource}));
      return;
    }
    props.setGgoAccount({connected:true,playerId:value.profile.id,displayName:value.profile.displayName,skinSource:value.profile.skinSource});
    if(!props.nickname) props.setNickname(value.profile.displayName);
  }

  async function ggoLogin(){
    props.setBusy(true);
    try{
      const status=await invoke<GgoAuthStatus>("ggo_login",{apiUrl:props.apiUrl});
      applyGgoStatus(status);
      props.setStatus(status.authenticated?"GGO account connected ✓":"GGO login was not completed");
    }catch(error){props.setStatus(String(error));}
    finally{props.setBusy(false);}
  }
  async function ggoLogout(){
    props.setBusy(true);
    try{
      await invoke("ggo_logout",{apiUrl:props.apiUrl});
      applyGgoStatus({authenticated:false,profile:null});
      props.setStatus("GGO signed out");
    }catch(error){props.setStatus(String(error));}
    finally{props.setBusy(false);}
  }
  async function setSkinSource(source:SkinSource){
    if(source==="ggo"&&!props.ggoAccount.connected)return;
    if(source==="microsoft"&&!microsoftConnected)return;
    if(props.ggoAccount.connected){
      props.setBusy(true);
      try{
        const status=await invoke<GgoAuthStatus>("ggo_set_skin_source",{apiUrl:props.apiUrl,source});
        applyGgoStatus(status);
        props.setStatus("Skin source updated ✓");
      }catch(error){props.setStatus(String(error));}
      finally{props.setBusy(false);}
    }else{
      props.setGgoAccount(current=>({...current,skinSource:source}));
    }
  }
  function useGuest(){
    props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:"default"}));
    setMode("guest");
    props.setStatus(`Guest · ${props.nickname.trim()||"Guest"}`);
  }

  return <section className="page accountsPage">
    <div className="accountHero">
      <div><span>{t.eyebrow}</span><h2>{t.title}</h2><p>{t.subtitle}</p></div>
      <div className="accountCurrent"><small>{t.current}</small><strong>{profileLabel}</strong><span>{props.ggoAccount.connected ? "GGO" : microsoftConnected ? "MICROSOFT" : "GUEST"}</span></div>
    </div>

    <div className="accountLayout">
      <div className="accountProviders">
        <button className={mode === "ggo" ? "providerCard selected" : "providerCard"} onClick={() => setMode("ggo")}>
          <div className="providerIcon ggoMark">G</div><div><strong>{t.ggo}</strong><p>{t.ggoBody}</p></div><span className={props.ggoAccount.connected ? "providerState on" : "providerState"}>{props.ggoAccount.connected ? t.linked : t.notLinked}</span>
        </button>
        <button className={mode === "microsoft" ? "providerCard selected" : "providerCard"} onClick={() => setMode("microsoft")}>
          <div className="providerIcon msMark"><i/><i/><i/><i/></div><div><strong>{t.microsoft}</strong><p>{t.microsoftBody}</p></div><span className={microsoftConnected ? "providerState on" : "providerState"}>{microsoftConnected ? t.linked : t.notLinked}</span>
        </button>
        <button className={mode === "guest" ? "providerCard selected" : "providerCard"} onClick={() => setMode("guest")}>
          <div className="providerIcon guestMark">↯</div><div><strong>{t.guest}</strong><p>{t.guestBody}</p></div><span className="providerState">LOCAL</span>
        </button>
      </div>

      <aside className="accountPanel">
        <small>{t.nickname}</small>
        <input className="accountName" value={props.nickname} maxLength={16} onChange={e => props.setNickname(e.target.value)} placeholder="Player name" />

        {mode === "ggo" && <div className="accountActionBlock">
          {props.ggoAccount.connected
            ? <><div className="linkedIdentity"><b>GGO</b><div><strong>{props.ggoAccount.displayName}</strong><span>{props.ggoAccount.playerId}</span></div></div>{microsoftConnected&&<button className="accountPrimary" disabled={props.busy} onClick={()=>void props.onLinkMinecraft()}>{t.linkMinecraft}</button>}<button className="accountSecondary" disabled={props.busy} onClick={()=>void ggoLogout()}>{t.logout}</button></>
            : <><button className="accountPrimary" disabled={props.busy} onClick={()=>void ggoLogin()}>{t.loginGgo}</button><p>{t.future}</p></>}
        </div>}

        {mode === "microsoft" && <div className="accountActionBlock">
          {microsoftConnected
            ? <><div className="linkedIdentity"><b>MS</b><div><strong>{microsoftName}</strong><span>Official Minecraft identity</span></div></div><button className="accountSecondary" disabled={props.busy} onClick={()=>void props.onMicrosoftLogout()}>{t.logout}</button></>
            : <button className="accountPrimary light" disabled={props.busy} onClick={()=>void props.onMicrosoftLogin()}>{t.loginMicrosoft}</button>}
        </div>}

        {mode === "guest" && <div className="accountActionBlock"><button className="accountPrimary ghost" disabled={props.busy} onClick={useGuest}>{t.useGuest}</button></div>}

        <div className="skinBlock">
          <small>{t.skins}</small>
          <div className="skinChoices">
            <button className={props.ggoAccount.skinSource === "ggo" ? "active" : ""} disabled={!props.ggoAccount.connected||props.busy} onClick={() => void setSkinSource("ggo")}><b>G</b><span>{t.ggoSkin}</span></button>
            <button className={props.ggoAccount.skinSource === "microsoft" ? "active" : ""} disabled={!microsoftConnected||props.busy} onClick={() => void setSkinSource("microsoft")}><b>MS</b><span>{t.msSkin}</span></button>
            <button className={props.ggoAccount.skinSource === "default" ? "active" : ""} disabled={props.busy} onClick={() => void setSkinSource("default")}><b>◎</b><span>{t.defaultSkin}</span></button>
          </div>
        </div>
      </aside>
    </div>
  </section>;
}
