export interface ActionDefinition {
  id: string;
  command: string;
  label: string;
  description: string;
  iconName: "Volume2" | "VolumeX" | "Flashlight" | "Lock" | "Vibrate" | "Camera" | "RefreshCw" | "Wifi";
  category: "alert" | "hardware" | "security" | "media";
  defaultValue: any;
  activeValue?: any;
}

export const REMOTE_ACTIONS_REGISTRY: ActionDefinition[] = [
  {
    id: "siren",
    command: "SIREN",
    label: "Play Siren",
    description: "Force loud anti-theft alarm at max volume for 30s",
    iconName: "Volume2",
    category: "alert",
    defaultValue: false,
    activeValue: true,
  },
  {
    id: "vibrate",
    command: "VIBRATE",
    label: "Vibrate Phone",
    description: "Trigger phone vibration even if in silent mode",
    iconName: "Vibrate",
    category: "alert",
    defaultValue: 3,
    activeValue: 3,
  },
  {
    id: "torch",
    command: "TORCH",
    label: "Torch Light",
    description: "Toggle device rear LED flashlight remotely",
    iconName: "Flashlight",
    category: "hardware",
    defaultValue: false,
    activeValue: true,
  },
  {
    id: "lock",
    command: "LOCK",
    label: "Lock Screen",
    description: "Instantly turn off screen and lock the child device",
    iconName: "Lock",
    category: "security",
    defaultValue: false,
    activeValue: true,
  },
  {
    id: "sync_media",
    command: "SYNC_MEDIA",
    label: "Sync Media & Files",
    description: "Force refresh gallery photos and storage file lists",
    iconName: "RefreshCw",
    category: "media",
    defaultValue: false,
    activeValue: true,
  },
  {
    id: "enable_net",
    command: "ENABLE_INTERNET",
    label: "Auto Internet On",
    description: "Request device accessibility to turn on mobile data/Wi-Fi",
    iconName: "Wifi",
    category: "hardware",
    defaultValue: false,
    activeValue: true,
  },
];

