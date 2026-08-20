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
    eyebrow:"IDENTITY",title:"Accounts",subtitle:"One GunGloryOnline identity, multiple ways to sign in.",
    ggo:"GGO Account",ggoBody:"Recommended. Your GGO profile owns progression, cosmetics and social data.",
    microsoft:"Microsoft",microsoftBody:"Optional official Minecraft identity that can be linked to GGO.",
    guest:"Quick Play",guestBody:"Nickname-only local profile. Cloud features are limited.",
    loginWeb:"SIGN IN THROUGH WEBSITE",loginPassword:"SIGN IN WITH PASSWORD",loginMicrosoft:"SIGN IN WITH MICROSOFT",useGuest:"USE GUEST PROFILE",logout:"Sign out",
    current:"Current profile",nickname:"Display name",username:"GGO username",password:"Password",skins:"Skin source",ggoSkin:"GGO skin",msSkin:"Official skin",defaultSkin:"GGO default",
    linked:"Linked",notLinked:"Not linked",linkMinecraft:"LINK MINECRAFT TO GGO",help:"Create the account on ggo.kvicloud.ru. Use username + password here, or open the website and approve this launcher like a device login."
  },
  ru: {
    eyebrow:"АККАУНТ",title:"Аккаунты",subtitle:"Один аккаунт GunGloryOnline и несколько способов входа.",
    ggo:"GGO аккаунт",ggoBody:"Основной вариант. GGO-профиль хранит прогресс, косметику и социальные функции.",
    microsoft:"Microsoft",microsoftBody:"Дополнительный официальный Minecraft-аккаунт, который можно привязать к GGO.",
    guest:"Быстрый вход",guestBody:"Локальный профиль только по нику. Облачные функции ограничены.",
    loginWeb:"ВОЙТИ ЧЕРЕЗ САЙТ",loginPassword:"ВОЙТИ ПО НИКУ И ПАРОЛЮ",loginMicrosoft:"ВОЙТИ ЧЕРЕЗ MICROSOFT",useGuest:"ИГРАТЬ КАК ГОСТЬ",logout:"Выйти",
    current:"Текущий профиль",nickname:"Отображаемый ник",username:"Ник GGO",password:"Пароль",skins:"Источник скина",ggoSkin:"Скин GGO",msSkin:"Официальный скин",defaultSkin:"Стандартный GGO",
    linked:"Привязан",notLinked:"Не привязан",linkMinecraft:"ПРИВЯЗАТЬ MINECRAFT К GGO",help:"Зарегистрируйся на ggo.kvicloud.ru. Можно войти здесь по нику и паролю или открыть сайт и подтвердить лаунчер как устройство."
  },
  uk: {
    eyebrow:"АКАУНТ",title:"Акаунти",subtitle:"Один акаунт GunGloryOnline і декілька способів входу.",
    ggo:"GGO акаунт",ggoBody:"Основний варіант. GGO-профіль зберігає прогрес, косметику та соціальні функції.",
    microsoft:"Microsoft",microsoftBody:"Додатковий офіційний Minecraft-акаунт, який можна прив'язати до GGO.",
    guest:"Швидкий вхід",guestBody:"Локальний профіль лише за ніком. Хмарні функції обмежені.",
    loginWeb:"УВІЙТИ ЧЕРЕЗ САЙТ",loginPassword:"УВІЙТИ ЗА НІКОМ І ПАРОЛЕМ",loginMicrosoft:"УВІЙТИ ЧЕРЕЗ MICROSOFT",useGuest:"ГРАТИ ЯК ГІСТЬ",logout:"Вийти",
    current:"Поточний профіль",nickname:"Ім'я гравця",username:"Нік GGO",password:"Пароль",skins:"Джерело скіна",ggoSkin:"Скін GGO",msSkin:"Офіційний скін",defaultSkin:"Стандартний GGO",
    linked:"Прив'язано",notLinked:"Не прив'язано",linkMinecraft:"ПРИВ'ЯЗАТИ MINECRAFT ДО GGO",help:"Зареєструйся на ggo.kvicloud.ru. Можна увійти тут за ніком і паролем або відкрити сайт і підтвердити лаунчер як пристрій."
  }
} as const;

export default function AccountHub(props: Props) {
  const t=text[props.lang];
  const microsoftConnected=props.microsoft.authenticated&&Boolean(props.microsoft.minecraftProfile);
  const microsoftName=props.microsoft.minecraftProfile?.name??null;
  const [mode,setMode]=useState<AccountMode>(props.ggoAccount.connected?"ggo":microsoftConnected?"microsoft":"guest");
  const [ggoUsername,setGgoUsername]=useState("");
  const [ggoPassword,setGgoPassword]=useState("");
  const profileLabel=useMemo(()=>{
    if(props.ggoAccount.connected)return props.ggoAccount.displayName||props.nickname||"GGO Player";
    if(microsoftConnected)return microsoftName||props.nickname||"Microsoft Player";
    return props.nickname||"Guest";
  },[props.ggoAccount,microsoftConnected,microsoftName,props.nickname]);

  function applyGgoStatus(value:GgoAuthStatus){
    if(!value.authenticated||!value.profile){
      props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:current.skinSource==="ggo"?"default":current.skinSource}));
      return;
    }
    props.setGgoAccount({connected:true,playerId:value.profile.id,displayName:value.profile.displayName,skinSource:value.profile.skinSource});
    props.setNickname(value.profile.displayName);
    setGgoPassword("");
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

  async function ggoPasswordLogin(){
    if(!ggoUsername.trim()||!ggoPassword){props.setStatus("Enter GGO username and password");return;}
    props.setBusy(true);
    try{
      const status=await invoke<GgoAuthStatus>("ggo_login",{apiUrl:props.apiUrl,username:ggoUsername.trim(),password:ggoPassword});
      applyGgoStatus(status);
      props.setStatus("GGO account connected ✓");
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
      try{const status=await invoke<GgoAuthStatus>("ggo_set_skin_source",{apiUrl:props.apiUrl,source});applyGgoStatus(status);props.setStatus("Skin source updated ✓");}
      catch(error){props.setStatus(String(error));}
      finally{props.setBusy(false);}
    }else props.setGgoAccount(current=>({...current,skinSource:source}));
  }

  function useGuest(){
    props.setGgoAccount(current=>({...current,connected:false,playerId:null,displayName:null,skinSource:"default"}));
    setMode("guest");props.setStatus(`Guest · ${props.nickname.trim()||"Guest"}`);
  }

  return <section className="page accountsPage">
    <div className="accountHero"><div><span>{t.eyebrow}</span><h2>{t.title}</h2><p>{t.subtitle}</p></div><div className="accountCurrent"><small>{t.current}</small><strong>{profileLabel}</strong><span>{props.ggoAccount.connected?"GGO":microsoftConnected?"MICROSOFT":"GUEST"}</span></div></div>
    <div className="accountLayout">
      <div className="accountProviders">
        <button className={mode==="ggo"?"providerCard selected":"providerCard"} onClick={()=>setMode("ggo")}><div className="providerIcon ggoMark">G</div><div><strong>{t.ggo}</strong><p>{t.ggoBody}</p></div><span className={props.ggoAccount.connected?"providerState on":"providerState"}>{props.ggoAccount.connected?t.linked:t.notLinked}</span></button>
        <button className={mode==="microsoft"?"providerCard selected":"providerCard"} onClick={()=>setMode("microsoft")}><div className="providerIcon msMark"><i/><i/><i/><i/></div><div><strong>{t.microsoft}</strong><p>{t.microsoftBody}</p></div><span className={microsoftConnected?"providerState on":"providerState"}>{microsoftConnected?t.linked:t.notLinked}</span></button>
        <button className={mode==="guest"?"providerCard selected":"providerCard"} onClick={()=>setMode("guest")}><div className="providerIcon guestMark">↯</div><div><strong>{t.guest}</strong><p>{t.guestBody}</p></div><span className="providerState">LOCAL</span></button>
      </div>
      <aside className="accountPanel">
        <small>{t.nickname}</small><input className="accountName" value={props.nickname} maxLength={16} onChange={e=>props.setNickname(e.target.value)} placeholder="Player name" />
        {mode==="ggo"&&<div className="accountActionBlock">
          {props.ggoAccount.connected?<><div className="linkedIdentity"><b>GGO</b><div><strong>{props.ggoAccount.displayName}</strong><span>{props.ggoAccount.playerId}</span></div></div>{microsoftConnected&&<button className="accountPrimary" disabled={props.busy} onClick={()=>void props.onLinkMinecraft()}>{t.linkMinecraft}</button>}<button className="accountSecondary" disabled={props.busy} onClick={()=>void ggoLogout()}>{t.logout}</button></>:<><label className="accountCredential"><small>{t.username}</small><input className="accountName" value={ggoUsername} maxLength={16} autoComplete="username" onChange={e=>setGgoUsername(e.target.value)} placeholder="PlayerName" /></label><label className="accountCredential"><small>{t.password}</small><input className="accountName" type="password" value={ggoPassword} autoComplete="current-password" onChange={e=>setGgoPassword(e.target.value)} placeholder="••••••••" /></label><button className="accountPrimary" disabled={props.busy} onClick={()=>void ggoPasswordLogin()}>{t.loginPassword}</button><div className="accountDivider"><span>OR</span></div><button className="accountSecondary" disabled={props.busy} onClick={()=>void ggoBrowserLogin()}>{t.loginWeb}</button><p>{t.help}</p></>}
        </div>}
        {mode==="microsoft"&&<div className="accountActionBlock">{microsoftConnected?<><div className="linkedIdentity"><b>MS</b><div><strong>{microsoftName}</strong><span>Official Minecraft identity</span></div></div><button className="accountSecondary" disabled={props.busy} onClick={()=>void props.onMicrosoftLogout()}>{t.logout}</button></>:<button className="accountPrimary light" disabled={props.busy} onClick={()=>void props.onMicrosoftLogin()}>{t.loginMicrosoft}</button>}</div>}
        {mode==="guest"&&<div className="accountActionBlock"><button className="accountPrimary ghost" disabled={props.busy} onClick={useGuest}>{t.useGuest}</button></div>}
        <div className="skinBlock"><small>{t.skins}</small><div className="skinChoices"><button className={props.ggoAccount.skinSource==="ggo"?"active":""} disabled={!props.ggoAccount.connected||props.busy} onClick={()=>void setSkinSource("ggo")}><b>G</b><span>{t.ggoSkin}</span></button><button className={props.ggoAccount.skinSource==="microsoft"?"active":""} disabled={!microsoftConnected||props.busy} onClick={()=>void setSkinSource("microsoft")}><b>MS</b><span>{t.msSkin}</span></button><button className={props.ggoAccount.skinSource==="default"?"active":""} disabled={props.busy} onClick={()=>void setSkinSource("default")}><b>◎</b><span>{t.defaultSkin}</span></button></div></div>
      </aside>
    </div>
  </section>;
}
