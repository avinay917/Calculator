import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getDatabase } from "firebase/database";
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
} from "firebase/firestore";
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

// Use persistent IndexedDB local cache across browser tabs for zero-cost repeat reads
export const firestore = (() => {
  try {
    return initializeFirestore(app, {
      localCache: persistentLocalCache({
        tabManager: persistentMultipleTabManager(),
      }),
    });
  } catch (_e) {
    return getFirestore(app);
  }
})();

export const storage = getStorage(app);

export default app;

