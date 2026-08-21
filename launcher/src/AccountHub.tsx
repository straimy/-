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
    eyebrow:"GGO IDENTITY",title:"Your GGO Account",subtitle:"One identity for progression, inventory, rank, cosmetics and every official GGO world.",
    online:"OFFICIAL ONLINE",onlineBody:"A GGO Account is required for official online play. Sign in safely through the GGO website.",
    training:"TRAINING PROFILE",trainingBody:"Local-only profile for Training. No rank, cloud inventory or online progression.",
    loginWeb:"SIGN IN THROUGH GGO WEBSITE",logout:"Sign out",current:"Current identity",nickname:"Training display name",skins:"Appearance source",ggoSkin:"GGO appearance",msSkin:"Linked legacy skin",defaultSkin:"GGO default",linked:"CONNECTED",notLinked:"SIGN IN REQUIRED",
    websiteHint:"Registration, password entry and account recovery happen on the GGO website. The launcher receives a device session after you approve it.",
    onlineLocked:"Official GGO Online is locked until a GGO Account is connected.",trainingUse:"USE LOCAL TRAINING PROFILE",local:"LOCAL ONLY",security:"Official servers require a GGO session ticket and client handshake. A normal Minecraft/third-party client cannot join official GGO worlds just by knowing the address."
  },
  ru: {
    eyebrow:"GGO IDENTITY",title:"Твой GGO аккаунт",subtitle:"Один аккаунт для прогресса, инвентаря, ранга, косметики и всех официальных миров GGO.",
    online:"ОФИЦИАЛЬНЫЙ ОНЛАЙН",onlineBody:"Для официальной онлайн-игры нужен GGO аккаунт. Вход выполняется безопасно через сайт GGO.",
    training:"ПРОФИЛЬ ТРЕНИРОВКИ",trainingBody:"Только локальный профиль для Training. Без ранга, облачного инвентаря и онлайн-прогресса.",
    loginWeb:"ВОЙТИ ЧЕРЕЗ САЙТ GGO",logout:"Выйти",current:"Текущая личность",nickname:"Ник для тренировки",skins:"Внешний вид",ggoSkin:"GGO внешний вид",msSkin:"Привязанный старый скин",defaultSkin:"Стандартный GGO",linked:"ПОДКЛЮЧЕН",notLinked:"НУЖЕН ВХОД",
    websiteHint:"Регистрация, пароль и восстановление аккаунта происходят на сайте GGO. Лаунчер получает сессию только после твоего подтверждения устройства.",
    onlineLocked:"Официальный GGO Online заблокирован, пока не подключён GGO аккаунт.",trainingUse:"ИСПОЛЬЗОВАТЬ ЛОКАЛЬНЫЙ ПРОФИЛЬ",local:"ТОЛЬКО ЛОКАЛЬНО",security:"Официальные серверы требуют GGO session ticket и клиентский handshake. Обычного Minecraft/стороннего лаунчера и знания адреса сервера недостаточно."
  },
  uk: {
    eyebrow:"GGO IDENTITY",title:"Твій GGO акаунт",subtitle:"Один акаунт для прогресу, інвентарю, рангу, косметики та всіх офіційних світів GGO.",
    online:"ОФІЦІЙНИЙ ОНЛАЙН",onlineBody:"Для офіційної онлайн-гри потрібен GGO акаунт. Вхід виконується через сайт GGO.",
    training:"ПРОФІЛЬ ТРЕНУВАННЯ",trainingBody:"Лише локальний профіль для Training. Без рангу, хмарного інвентарю та онлайн-прогресу.",
    loginWeb:"УВІЙТИ ЧЕРЕЗ САЙТ GGO",logout:"Вийти",current:"Поточна особа",nickname:"Ім'я для тренування",skins:"Зовнішній вигляд",ggoSkin:"GGO вигляд",msSkin:"Прив'язаний старий скін",defaultSkin:"Стандартний GGO",linked:"ПІДКЛЮЧЕНО",notLinked:"ПОТРІБЕН ВХІД",
    websiteHint:"Реєстрація, пароль і відновлення акаунта відбуваються на сайті GGO. Лаунчер отримує сесію лише після підтвердження пристрою.",
    onlineLocked:"Офіційний GGO Online заблоковано, доки не підключено GGO акаунт.",trainingUse:"ВИКОРИСТАТИ ЛОКАЛЬНИЙ ПРОФІЛЬ",local:"ЛИШЕ ЛОКАЛЬНО",security:"Офіційні сервери вимагають GGO session ticket і клієнтський handshake. Звичайного Minecraft/стороннього лаунчера та адреси сервера недостатньо."
  }
} as const;

export default function AccountHub(props: Props) {
  const t=text[props.lang];
  const microsoftConnected=props.microsoft.authenticated&&Boolean(props.microsoft.minecraftProfile);
  const [localTraining,setLocalTraining]=useState(!props.ggoAccount.connected);
  const profileLabel=useMemo(()=>props.ggoAccount.connected?(props.ggoAccount.displayName||"GGO Player"):(props.nickname||"Training Player"),[props.ggoAccount,props.nickname]);

  function applyGgoStatus(value:GgoAuthStatus){
    if(!value.authenticated||!value.profile){
      props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:current.skinSource==="ggo"?"default":current.skinSource}));
      setLocalTraining(true);
      return;
    }
    props.setGgoAccount({connected:true,playerId:value.profile.id,displayName:value.profile.displayName,skinSource:value.profile.skinSource});
    props.setNickname(value.profile.displayName);
    setLocalTraining(false);
  }

  async function ggoBrowserLogin(){
    props.setBusy(true);
    try{
      const status=await invoke<GgoAuthStatus>("ggo_login",{apiUrl:props.apiUrl,username:null,password:null});
      applyGgoStatus(status);
      props.setStatus(status.authenticated?"GGO account connected ✓":"GGO login was not completed");
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

  function useTrainingProfile(){
    setLocalTraining(true);
    props.setStatus(`Training profile · ${props.nickname.trim()||"Training Player"}`);
  }

  return <section className="page accountsPage">
    <div className="accountHero"><div><span>{t.eyebrow}</span><h2>{t.title}</h2><p>{t.subtitle}</p></div><div className="accountCurrent"><small>{t.current}</small><strong>{profileLabel}</strong><span>{props.ggoAccount.connected?"GGO ONLINE":t.local}</span></div></div>
    <div className="accountLayout">
      <div className="accountProviders">
        <div className="providerCard selected"><div className="providerIcon ggoMark">G</div><div><strong>{t.online}</strong><p>{t.onlineBody}</p></div><span className={props.ggoAccount.connected?"providerState on":"providerState"}>{props.ggoAccount.connected?t.linked:t.notLinked}</span></div>
        <button className={localTraining&&!props.ggoAccount.connected?"providerCard selected":"providerCard"} onClick={useTrainingProfile}><div className="providerIcon guestMark">↯</div><div><strong>{t.training}</strong><p>{t.trainingBody}</p></div><span className="providerState">{t.local}</span></button>
        <div className="providerCard"><div className="providerIcon guestMark">✓</div><div><strong>GGO SESSION SECURITY</strong><p>{t.security}</p></div><span className="providerState on">ONLINE</span></div>
      </div>
      <aside className="accountPanel">
        {props.ggoAccount.connected?<div className="accountActionBlock"><div className="linkedIdentity"><b>GGO</b><div><strong>{props.ggoAccount.displayName}</strong><span>{props.ggoAccount.playerId}</span></div></div><button className="accountSecondary" disabled={props.busy} onClick={()=>void ggoLogout()}>{t.logout}</button></div>:<div className="accountActionBlock"><button className="accountPrimary" disabled={props.busy} onClick={()=>void ggoBrowserLogin()}>{t.loginWeb}</button><p>{t.websiteHint}</p><p>{t.onlineLocked}</p></div>}
        {!props.ggoAccount.connected&&<div className="accountActionBlock"><small>{t.nickname}</small><input className="accountName" value={props.nickname} maxLength={16} onChange={e=>props.setNickname(e.target.value)} placeholder="Training Player"/><button className="accountSecondary" disabled={props.busy} onClick={useTrainingProfile}>{t.trainingUse}</button></div>}
        <div className="skinBlock"><small>{t.skins}</small><div className="skinChoices"><button className={props.ggoAccount.skinSource==="ggo"?"active":""} disabled={!props.ggoAccount.connected||props.busy} onClick={()=>void setSkinSource("ggo")}><b>G</b><span>{t.ggoSkin}</span></button><button className={props.ggoAccount.skinSource==="microsoft"?"active":""} disabled={!microsoftConnected||props.busy} onClick={()=>void setSkinSource("microsoft")}><b>↗</b><span>{t.msSkin}</span></button><button className={props.ggoAccount.skinSource==="default"?"active":""} disabled={props.busy} onClick={()=>void setSkinSource("default")}><b>◎</b><span>{t.defaultSkin}</span></button></div></div>
      </aside>
    </div>
  </section>;
}
