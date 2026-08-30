use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GunGloryIdentity {
    pub ggo_player_id: Uuid,
    pub created_at_unix_ms: i64,
    pub display_name: String,
    pub external_identities: Vec<ExternalIdentity>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExternalIdentity {
    pub provider: IdentityProviderKind,
    pub external_id: String,
    pub external_name: String,
}

#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum IdentityProviderKind {
    Minecraft,
    Steam,
}

pub trait IdentityProvider {
    type Error;
    fn authenticate(&mut self) -> Result<ExternalIdentity, Self::Error>;
    fn refresh_session(&mut self) -> Result<(), Self::Error>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LegacyMinecraftProfile {
    pub minecraft_uuid: String,
    pub minecraft_name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProfileLinkRequest {
    pub ggo_player_id: Uuid,
    pub legacy: LegacyMinecraftProfile,
}
