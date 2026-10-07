import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";

const firebaseConfig = {
  apiKey: "AIzaSyB2aHZi5n4KGD6Zf_OISpWFZVNI20Qysq0",
  authDomain: "apnasatthilko.firebaseapp.com",
  databaseURL: "https://apnasatthilko-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "apnasatthilko",
  storageBucket: "apnasatthilko.firebasestorage.app",
  messagingSenderId: "247813001651",
  appId: "1:247813001651:web:parentportal",
};

// Initialize Firebase only once
const app = !getApps().length ? initializeApp(firebaseConfig) : getApp();

export const auth = getAuth(app);
export const rtdb = getDatabase(app);
export const firestore = getFirestore(app);
export const storage = getStorage(app);

export default app;

