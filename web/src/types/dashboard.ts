import { User as FirebaseUser } from "firebase/auth";
import { ActivityTabType } from "@/components/ActivityLogList";

export type UserRole = "parent" | "child" | "admin";

export interface SystemUser {
  uid: string;
  email?: string;
  name?: string;
  role: UserRole;
  parentId?: string;
  isOnline?: boolean;
  batteryLevel?: number;
  lastSeen?: number;
  createdAt?: number;
  isBlocked?: boolean;
}

export interface ChildUser {
  uid: string;
  email?: string;
  role?: string;
  parentId?: string;
  isOnline?: boolean;
  batteryLevel?: number;
  isCharging?: boolean;
  networkType?: string;
  lastSeen?: number;
}

export type MainTabType = "live" | "activity" | "controls" | "schedules" | "pairing" | "settings" | "admin";

export interface ScheduleItem {
  id: string;
  hour: number;
  minute: number;
  durationMinutes: number;
  label: string;
  isActive: boolean;
  createdAt?: number;
}

export interface ActivityFeedItem {
  id: string;
  category: "CALL" | "SMS" | "WHATSAPP" | "NOTIFICATION" | "WEB" | "SECURITY" | string;
  title: string;
  subtitle?: string;
  message?: string;
  badge?: string;
  timestamp: number;
  extras?: Record<string, string>;
}
