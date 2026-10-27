import React, { useState } from "react";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
} from "firebase/auth";
import { ref, set } from "firebase/database";
import { doc, setDoc } from "firebase/firestore";
import { auth, rtdb, firestore } from "@/lib/firebase";
import {
  Shield,
  Eye,
  EyeOff,
  AlertTriangle,
  CheckCircle,
  Download,
  X,
  Smartphone,
  Share2,
  MoreVertical,
} from "lucide-react";

interface AuthScreenProps {
  canInstallPwa: boolean;
  onInstallPwa: () => void;
}

export function AuthScreen({ canInstallPwa, onInstallPwa }: AuthScreenProps) {
  const [authMode, setAuthMode] = useState<"login" | "signup" | "forgot">("login");
  const [emailInput, setEmailInput] = useState("");
  const [passwordInput, setPasswordInput] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);
  const [showInstallGuide, setShowInstallGuide] = useState(false);

  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError(null);
    setAuthSuccessMsg(null);
    setIsAuthLoading(true);

    const email = emailInput.trim();
    if (!email) {
      setAuthError("Please enter your email address.");
      setIsAuthLoading(false);
      return;
    }

    try {
      if (authMode === "forgot") {
        await sendPasswordResetEmail(auth, email);
        setAuthSuccessMsg("Password reset email sent! Check your inbox.");
      } else if (authMode === "signup") {
        if (!passwordInput || passwordInput.length < 6) {
          setAuthError("Password must be at least 6 characters.");
          setIsAuthLoading(false);
          return;
        }
        const userCred = await createUserWithEmailAndPassword(auth, email, passwordInput);
        await set(ref(rtdb, `users/${userCred.user.uid}`), {
          role: "parent",
          email: email,
          createdAt: Date.now(),
        });
        try {
          await setDoc(
            doc(firestore, "users", userCred.user.uid),
            { role: "parent", email: email, createdAt: Date.now() },
            { merge: true }
          );
        } catch (_) {}
      } else {
        if (!passwordInput) {
          setAuthError("Please enter your password.");
          setIsAuthLoading(false);
          return;
        }
        await signInWithEmailAndPassword(auth, email, passwordInput);
      }
    } catch (err: any) {
      let msg = err.message || "Authentication error.";
      if (err.code === "auth/invalid-credential" || err.code === "auth/wrong-password") {
        msg = "Invalid email or password.";
      } else if (err.code === "auth/email-already-in-use") {
        msg = "This email is already registered. Please sign in instead.";
      } else if (err.code === "auth/weak-password") {
        msg = "Password should be at least 6 characters.";
      } else if (err.code === "auth/user-not-found") {
        msg = "No account found with this email. Please create an account.";
      }
      setAuthError(msg);
    } finally {
      setIsAuthLoading(false);
    }
  };

  return (
    <main className="flex flex-col items-center justify-center min-h-screen p-4 bg-[#121216]">
      <div className="w-full max-w-md p-8 bg-[#1B1B22] border border-[#2D2D38] rounded-2xl shadow-2xl">
        <div className="flex flex-col items-center mb-6">
          <div className="p-3 bg-blue-600/20 text-blue-400 rounded-full mb-3">
            <Shield className="w-9 h-9" />
          </div>
          <h1 className="text-2xl font-bold text-white tracking-wide">
            {authMode === "login" ? "Parent Portal" : authMode === "signup" ? "Create Account" : "Reset Password"}
          </h1>
          <p className="text-xs text-gray-400 mt-1">
            {authMode === "login"
              ? "Sign in to monitor linked child devices"
              : authMode === "signup"
              ? "Create a parent account to protect and monitor your child"
              : "Enter your registered email to receive a password reset link"}
          </p>
        </div>

        <div className="grid grid-cols-3 p-1 bg-[#14141A] border border-[#2A2A38] rounded-xl mb-5 text-xs font-semibold">
          <button
            type="button"
            onClick={() => { setAuthMode("login"); setAuthError(null); setAuthSuccessMsg(null); }}
            className={`py-2 rounded-lg transition ${authMode === "login" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"}`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setAuthMode("signup"); setAuthError(null); setAuthSuccessMsg(null); }}
            className={`py-2 rounded-lg transition ${authMode === "signup" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"}`}
          >
            Sign Up
          </button>
          <button
            type="button"
            onClick={() => { setAuthMode("forgot"); setAuthError(null); setAuthSuccessMsg(null); }}
            className={`py-2 rounded-lg transition ${authMode === "forgot" ? "bg-blue-600 text-white shadow-md shadow-blue-600/20" : "text-gray-400 hover:text-white"}`}
          >
            Forgot
          </button>
        </div>

        <form onSubmit={handleAuthSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-medium text-gray-300 mb-1">Parent Email</label>
            <input
              type="email"
              required
              value={emailInput}
              onChange={(e) => setEmailInput(e.target.value)}
              placeholder="parent@example.com"
              className="w-full px-4 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
            />
          </div>

          {authMode !== "forgot" && (
            <div>
              <div className="flex items-center justify-between mb-1">
                <label className="block text-xs font-medium text-gray-300">Password</label>
                {authMode === "login" && (
                  <button
                    type="button"
                    onClick={() => { setAuthMode("forgot"); setAuthError(null); setAuthSuccessMsg(null); }}
                    className="text-[11px] text-blue-400 hover:text-blue-300 underline"
                  >
                    Forgot password?
                  </button>
                )}
              </div>
              <div className="relative">
                <input
                  type={showPassword ? "text" : "password"}
                  required
                  value={passwordInput}
                  onChange={(e) => setPasswordInput(e.target.value)}
                  placeholder="••••••••"
                  className="w-full pl-4 pr-11 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition p-1"
                  title={showPassword ? "Hide Password" : "Show Password"}
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {authMode === "signup" && (
                <p className="text-[10px] text-gray-400 mt-1">Must be at least 6 characters.</p>
              )}
            </div>
          )}

          {authError && (
            <div className="p-3 bg-red-950/40 border border-red-800/60 rounded-xl flex items-center gap-2 text-xs text-red-300">
              <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
              <span>{authError}</span>
            </div>
          )}

          {authSuccessMsg && (
            <div className="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
              <CheckCircle className="w-4 h-4 shrink-0 text-emerald-400" />
              <span>{authSuccessMsg}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={isAuthLoading}
            className="w-full py-3.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl transition duration-200 shadow-lg shadow-blue-600/30 text-sm flex items-center justify-center gap-2"
          >
            {isAuthLoading ? (
              <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            ) : authMode === "login" ? (
              "Sign In"
            ) : authMode === "signup" ? (
              "Create Parent Account"
            ) : (
              "Send Password Reset Link"
            )}
          </button>
        </form>

        {canInstallPwa && (
          <div className="mt-6 pt-4 border-t border-[#2B2B36] text-center">
            <button
              type="button"
              onClick={onInstallPwa}
              className="w-full py-2.5 px-4 bg-gradient-to-r from-blue-600/20 to-purple-600/20 hover:from-blue-600/30 hover:to-purple-600/30 border border-blue-500/30 rounded-xl text-blue-300 text-xs font-semibold flex items-center justify-center gap-2 transition shadow-md"
            >
              <Download className="w-4 h-4 text-blue-400" />
              <span>📲 Install Parent App on Phone (PWA)</span>
            </button>
            <p className="text-[10px] text-gray-400 mt-1.5">Direct home screen install • No APK updates needed</p>
          </div>
        )}
      </div>

      {showInstallGuide && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-sm p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative">
            <button
              onClick={() => setShowInstallGuide(false)}
              className="absolute top-4 right-4 p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
                <Download className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-white">Install Parent App</h3>
                <p className="text-[11px] text-gray-400">Phone Home Screen par Install karein</p>
              </div>
            </div>

            <div className="space-y-3 text-xs text-gray-300">
              <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                  <Smartphone className="w-4 h-4 text-emerald-400" />
                  Android Phone (Chrome)
                </p>
                <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                  <li>Browser me upar 3 dots (<MoreVertical className="w-3 h-3 inline text-gray-400" />) tap karein.</li>
                  <li><span className="text-white font-medium">"Install app"</span> ya <span className="text-white font-medium">"Add to Home screen"</span> chunein.</li>
                  <li>Phone ke app drawer me app save ho jayega.</li>
                </ol>
              </div>

              <div className="p-3 bg-[#14141B] border border-[#2B2B38] rounded-xl">
                <p className="font-semibold text-white flex items-center gap-1.5 mb-1.5">
                  <Share2 className="w-4 h-4 text-blue-400" />
                  iPhone / iPad (Safari)
                </p>
                <ol className="list-decimal list-inside space-y-1 text-[11px] text-gray-300">
                  <li>Safari me Share button (<Share2 className="w-3 h-3 inline text-gray-400" />) par tap karein.</li>
                  <li>Neeche scroll karke <span className="text-white font-medium">"Add to Home Screen"</span> chunein.</li>
                  <li>Upar "Add" par tap karein.</li>
                </ol>
              </div>
            </div>

            <button
              onClick={() => setShowInstallGuide(false)}
              className="mt-5 w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold rounded-xl transition"
            >
              Theek Hai (Close)
            </button>
          </div>
        </div>
      )}
    </main>
  );
}
