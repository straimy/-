import { useMemo, useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import "./account.css";

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
    eyebrow:"GGO IDENTITY",title:"Your GGO Account",subtitle:"One account for official online play, progression, inventory, rank and cosmetics.",
    online:"GGO ACCOUNT",onlineBody:"Sign in or create an account in the official GGO Account page. The launcher connects automatically after browser approval.",
    loginWeb:"SIGN IN / CREATE ACCOUNT",logout:"Sign out",current:"Current identity",skins:"Appearance source",ggoSkin:"GGO appearance",msSkin:"Linked legacy skin",defaultSkin:"GGO default",linked:"CONNECTED",notLinked:"SIGN IN REQUIRED",
    websiteHint:"Account creation and password entry stay on the official GGO Account page. After approval, this launcher receives the session automatically.",
    onlineLocked:"Official Online requires a connected GGO Account.",trainingTitle:"OFFLINE TRAINING FALLBACK",trainingBody:"Training can still run without an online account. This local name never becomes a second online identity.",nickname:"Offline training name",trainingUse:"USE OFFLINE TRAINING",local:"OFFLINE",security:"PLAY ONLINE creates a short one-shot GGO ticket. The server verifies it before gameplay is unlocked."
  },
  ru: {
    eyebrow:"GGO IDENTITY",title:"Твой GGO аккаунт",subtitle:"Один аккаунт для официальной онлайн-игры, прогресса, инвентаря, ранга и косметики.",
    online:"GGO ACCOUNT",onlineBody:"Войди или создай аккаунт на официальной странице GGO Account. После подтверждения браузера лаунчер подключится автоматически.",
    loginWeb:"ВОЙТИ / СОЗДАТЬ АККАУНТ",logout:"Выйти",current:"Текущий аккаунт",skins:"Внешний вид",ggoSkin:"GGO внешний вид",msSkin:"Привязанный старый скин",defaultSkin:"Стандартный GGO",linked:"ПОДКЛЮЧЕН",notLinked:"НУЖЕН ВХОД",
    websiteHint:"Регистрация и ввод пароля происходят только в официальном GGO Account UI. После подтверждения лаунчер сам получает сессию.",
    onlineLocked:"Для PLAY ONLINE нужен подключённый GGO Account.",trainingTitle:"OFFLINE TRAINING FALLBACK",trainingBody:"Тренировка может работать без онлайн-аккаунта. Локальный ник не является второй системой аккаунтов и не используется в Official Online.",nickname:"Ник для офлайн-тренировки",trainingUse:"ИСПОЛЬЗОВАТЬ OFFLINE TRAINING",local:"OFFLINE",security:"PLAY ONLINE создаёт короткий одноразовый GGO ticket. Сервер проверяет его до разблокировки геймплея."
  },
  uk: {
    eyebrow:"GGO IDENTITY",title:"Твій GGO акаунт",subtitle:"Один акаунт для офіційної онлайн-гри, прогресу, інвентарю, рангу та косметики.",
    online:"GGO ACCOUNT",onlineBody:"Увійди або створи акаунт на офіційній сторінці GGO Account. Після підтвердження в браузері лаунчер підключиться автоматично.",
    loginWeb:"УВІЙТИ / СТВОРИТИ АКАУНТ",logout:"Вийти",current:"Поточний акаунт",skins:"Зовнішній вигляд",ggoSkin:"GGO вигляд",msSkin:"Прив'язаний старий скін",defaultSkin:"Стандартний GGO",linked:"ПІДКЛЮЧЕНО",notLinked:"ПОТРІБЕН ВХІД",
    websiteHint:"Реєстрація та пароль залишаються лише в офіційному GGO Account UI. Після підтвердження лаунчер сам отримує сесію.",
    onlineLocked:"Для PLAY ONLINE потрібен підключений GGO Account.",trainingTitle:"OFFLINE TRAINING FALLBACK",trainingBody:"Тренування може працювати без онлайн-акаунта. Локальне ім'я не є другою системою акаунтів і не використовується в Official Online.",nickname:"Ім'я для офлайн-тренування",trainingUse:"ВИКОРИСТАТИ OFFLINE TRAINING",local:"OFFLINE",security:"PLAY ONLINE створює короткий одноразовий GGO ticket. Сервер перевіряє його до розблокування геймплею."
  }
} as const;

export default function AccountHub(props: Props) {
  const t=text[props.lang];
  const microsoftConnected=props.microsoft.authenticated&&Boolean(props.microsoft.minecraftProfile);
  const [offlineTraining,setOfflineTraining]=useState(!props.ggoAccount.connected);
  const profileLabel=useMemo(()=>props.ggoAccount.connected?(props.ggoAccount.displayName||"GGO Player"):(props.nickname||"Training Player"),[props.ggoAccount,props.nickname]);

  function applyGgoStatus(value:GgoAuthStatus){
    if(!value.authenticated||!value.profile){
      props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:current.skinSource==="ggo"?"default":current.skinSource}));
      setOfflineTraining(true);
      return;
    }
    props.setGgoAccount({connected:true,playerId:value.profile.id,displayName:value.profile.displayName,skinSource:value.profile.skinSource});
    props.setNickname(value.profile.displayName);
    setOfflineTraining(false);
  }

  async function ggoBrowserLogin(){
    props.setBusy(true);
    try{
      const status=await invoke<GgoAuthStatus>("ggo_login",{apiUrl:props.apiUrl,username:null,password:null});
      applyGgoStatus(status);
      props.setStatus(status.authenticated?"GGO Account connected ✓":"GGO sign-in was not completed");
    }catch(error){props.setStatus(String(error));}
    finally{props.setBusy(false);}
  }

  async function ggoLogout(){
    props.setBusy(true);
    try{await invoke("ggo_logout",{apiUrl:props.apiUrl});applyGgoStatus({authenticated:false,profile:null});props.setStatus("GGO signed out");}
    catch(error){props.setStatus(String(error));}
    finally{props.setBusy(false);}
  }

  async function setSkinSource(source:SkinSource){
    if(source==="ggo"&&!props.ggoAccount.connected)return;
    if(source==="microsoft"&&!microsoftConnected)return;
    if(props.ggoAccount.connected){
      props.setBusy(true);
      try{const status=await invoke<GgoAuthStatus>("ggo_set_skin_source",{apiUrl:props.apiUrl,source});applyGgoStatus(status);props.setStatus("Appearance updated ✓");}
      catch(error){props.setStatus(String(error));}
      finally{props.setBusy(false);}
    }else props.setGgoAccount(current=>({...current,skinSource:source}));
  }

  function useOfflineTraining(){
    setOfflineTraining(true);
    props.setStatus(`Offline Training · ${props.nickname.trim()||"Training Player"}`);
  }

  return <section className="page accountsPage">
    <div className="accountHero"><div><span>{t.eyebrow}</span><h2>{t.title}</h2><p>{t.subtitle}</p></div><div className="accountCurrent"><small>{t.current}</small><strong>{profileLabel}</strong><span>{props.ggoAccount.connected?"GGO ONLINE":t.local}</span></div></div>
    <div className="accountLayout">
      <div className="accountProviders">
        <div className="providerCard selected"><div className="providerIcon ggoMark">G</div><div><strong>{t.online}</strong><p>{t.onlineBody}</p></div><span className={props.ggoAccount.connected?"providerState on":"providerState"}>{props.ggoAccount.connected?t.linked:t.notLinked}</span></div>
        <div className="providerCard"><div className="providerIcon guestMark">✓</div><div><strong>GGO SESSION SECURITY</strong><p>{t.security}</p></div><span className="providerState on">ONLINE</span></div>
      </div>
      <aside className="accountPanel">
        {props.ggoAccount.connected?<div className="accountActionBlock"><div className="linkedIdentity"><b>GGO</b><div><strong>{props.ggoAccount.displayName}</strong><span>{props.ggoAccount.playerId}</span></div></div><button className="accountSecondary" disabled={props.busy} onClick={()=>void ggoLogout()}>{t.logout}</button></div>:<div className="accountActionBlock"><button className="accountPrimary" disabled={props.busy} onClick={()=>void ggoBrowserLogin()}>{t.loginWeb}</button><p>{t.websiteHint}</p><p>{t.onlineLocked}</p></div>}
        {!props.ggoAccount.connected&&<div className="accountActionBlock"><small>{t.trainingTitle}</small><p>{t.trainingBody}</p><small>{t.nickname}</small><input className="accountName" value={props.nickname} maxLength={16} onChange={e=>props.setNickname(e.target.value)} placeholder="Training Player"/><button className="accountSecondary" disabled={props.busy||offlineTraining} onClick={useOfflineTraining}>{offlineTraining?t.local:t.trainingUse}</button></div>}
        <div className="skinBlock"><small>{t.skins}</small><div className="skinChoices"><button className={props.ggoAccount.skinSource==="ggo"?"active":""} disabled={!props.ggoAccount.connected||props.busy} onClick={()=>void setSkinSource("ggo")}><b>G</b><span>{t.ggoSkin}</span></button><button className={props.ggoAccount.skinSource==="microsoft"?"active":""} disabled={!microsoftConnected||props.busy} onClick={()=>void setSkinSource("microsoft")}><b>↗</b><span>{t.msSkin}</span></button><button className={props.ggoAccount.skinSource==="default"?"active":""} disabled={props.busy} onClick={()=>void setSkinSource("default")}><b>◎</b><span>{t.defaultSkin}</span></button></div></div>
      </aside>
    </div>
  </section>;
}
