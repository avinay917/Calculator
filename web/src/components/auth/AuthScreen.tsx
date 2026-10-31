import React, { useState } from "react";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  sendEmailVerification,
} from "firebase/auth";
import { ref, set } from "firebase/database";
import { auth, rtdb } from "@/lib/firebase";
import {
  Shield,
  Eye,
  EyeOff,
  AlertTriangle,
  CheckCircle,
  Download,
  X,
  Mail,
  KeyRound,
  User as UserIcon,
} from "lucide-react";

interface AuthScreenProps {
  canInstallPwa: boolean;
  onInstallPwa: () => void;
}

export function AuthScreen({ canInstallPwa, onInstallPwa }: AuthScreenProps) {
  const [authMode, setAuthMode] = useState<"login" | "signup">("login");
  const [fullNameInput, setFullNameInput] = useState("");
  const [emailInput, setEmailInput] = useState("");
  const [passwordInput, setPasswordInput] = useState("");
  const [confirmPasswordInput, setConfirmPasswordInput] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);
  const [isAuthLoading, setIsAuthLoading] = useState(false);

  // Email verification resend with 1-min cooldown
  const [isUnverifiedEmail, setIsUnverifiedEmail] = useState(false);
  const [resendCooldown, setResendCooldown] = useState(0);
  const [isResending, setIsResending] = useState(false);

  // 1-minute countdown timer effect
  React.useEffect(() => {
    if (resendCooldown <= 0) return;
    const interval = setInterval(() => {
      setResendCooldown((prev) => (prev > 1 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(interval);
  }, [resendCooldown]);

  // Inline Forgot Password Modal on the Login Screen
  const [showForgotModal, setShowForgotModal] = useState(false);
  const [forgotEmail, setForgotEmail] = useState("");
  const [forgotLoading, setForgotLoading] = useState(false);
  const [forgotError, setForgotError] = useState<string | null>(null);
  const [forgotSuccess, setForgotSuccess] = useState<string | null>(null);

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
      if (authMode === "signup") {
        const name = fullNameInput.trim();
        if (!name) {
          setAuthError("Please enter your full name.");
          setIsAuthLoading(false);
          return;
        }
        if (!passwordInput || passwordInput.length < 6) {
          setAuthError("Password must be at least 6 characters.");
          setIsAuthLoading(false);
          return;
        }
        if (passwordInput !== confirmPasswordInput) {
          setAuthError("Passwords do not match. Please re-check.");
          setIsAuthLoading(false);
          return;
        }

        const userCred = await createUserWithEmailAndPassword(auth, email, passwordInput);
        const uid = userCred.user.uid;

        // Backend security rules enforce role: "parent" (admin cannot be created here)
        await set(ref(rtdb, `users/${uid}`), {
          name: name,
          email: email,
          role: "parent",
          createdAt: Date.now(),
        });

        // Send email verification link
        try {
          await sendEmailVerification(userCred.user);
        } catch (_) {}

        // Sign out newly created unverified account and prompt verification
        await auth.signOut();
        setFullNameInput("");
        setPasswordInput("");
        setConfirmPasswordInput("");
        setAuthMode("login");
        setAuthSuccessMsg("Account safaltapoorvak ban gaya! Verification link aapke email par bhej diya gaya hai. Kripya pehle email verify karein, fir login karein.");
      } else {
        if (!passwordInput) {
          setAuthError("Please enter your password.");
          setIsAuthLoading(false);
          return;
        }
        const userCred = await signInWithEmailAndPassword(auth, email, passwordInput);
        if (!userCred.user.emailVerified) {
          try {
            await sendEmailVerification(userCred.user);
          } catch (_) {}
          await auth.signOut();
          setIsUnverifiedEmail(true);
          setResendCooldown(60); // 1-minute cooldown
          setAuthError("Aapka email verify nahi hai! Verification link aapke inbox mein bhej diya gaya hai. Kripya link par click karke verify karein.");
          return;
        }
        setIsUnverifiedEmail(false);
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

  const handleResendVerification = async () => {
    if (resendCooldown > 0 || isResending) return;
    const email = emailInput.trim();
    if (!email || !passwordInput) {
      setAuthError("Kripya email aur password daalein taaki verification link dobara bheja ja sake.");
      return;
    }
    setIsResending(true);
    setAuthError(null);
    setAuthSuccessMsg(null);
    try {
      const userCred = await signInWithEmailAndPassword(auth, email, passwordInput);
      await sendEmailVerification(userCred.user);
      await auth.signOut();
      setAuthSuccessMsg("Naya verification link aapke email par bhej diya gaya hai! Kripya inbox check karein.");
      setResendCooldown(60); // 1-minute expiration/cooldown
    } catch (err: any) {
      if (err.code === "auth/too-many-requests") {
        setAuthError("Bahut saare requests ho gaye hain. Kripya 1 minute baad koshish karein.");
        setResendCooldown(60);
      } else {
        setAuthError(err.message || "Verification link bhejne mein samasya.");
      }
    } finally {
      setIsResending(false);
    }
  };

  const handleSendResetLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setForgotError(null);
    setForgotSuccess(null);
    const targetEmail = (forgotEmail || emailInput).trim();
    if (!targetEmail) {
      setForgotError("Please enter your registered email address.");
      return;
    }
    setForgotLoading(true);
    try {
      await sendPasswordResetEmail(auth, targetEmail);
      setForgotSuccess("Password reset link sent! Please check your email inbox.");
    } catch (err: any) {
      let msg = err.message || "Failed to send reset email.";
      if (err.code === "auth/user-not-found") {
        msg = "No account registered with this email.";
      }
      setForgotError(msg);
    } finally {
      setForgotLoading(false);
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
            {authMode === "login" ? "Parent Portal" : "Create Account"}
          </h1>
          <p className="text-xs text-gray-400 mt-1">
            {authMode === "login"
              ? "Sign in to monitor linked child devices"
              : "Register as parent to monitor and protect child devices"}
          </p>
        </div>

        {/* 2-tab switch: Sign In vs Sign Up (Forgot password removed from here) */}
        <div className="grid grid-cols-2 p-1 bg-[#14141A] border border-[#2A2A38] rounded-xl mb-5 text-xs font-semibold">
          <button
            type="button"
            onClick={() => {
              setAuthMode("login");
              setAuthError(null);
              setAuthSuccessMsg(null);
            }}
            className={`py-2 rounded-lg transition ${
              authMode === "login"
                ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => {
              setAuthMode("signup");
              setAuthError(null);
              setAuthSuccessMsg(null);
            }}
            className={`py-2 rounded-lg transition ${
              authMode === "signup"
                ? "bg-blue-600 text-white shadow-md shadow-blue-600/20"
                : "text-gray-400 hover:text-white"
            }`}
          >
            Sign Up
          </button>
        </div>

        <form onSubmit={handleAuthSubmit} className="space-y-4">
          {/* Full Name field in Signup */}
          {authMode === "signup" && (
            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">
                Full Name
              </label>
              <div className="relative">
                <input
                  type="text"
                  required
                  value={fullNameInput}
                  onChange={(e) => setFullNameInput(e.target.value)}
                  placeholder="Enter your full name"
                  className="w-full pl-10 pr-4 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
                />
                <UserIcon className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-500" />
              </div>
            </div>
          )}

          {/* Email field */}
          <div>
            <label className="block text-xs font-medium text-gray-300 mb-1">
              Parent Email
            </label>
            <div className="relative">
              <input
                type="email"
                required
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                placeholder="parent@example.com"
                className="w-full pl-10 pr-4 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
              />
              <Mail className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-500" />
            </div>
          </div>

          {/* Password field */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="block text-xs font-medium text-gray-300">
                {authMode === "signup" ? "Create Password" : "Password"}
              </label>
              {authMode === "login" && (
                <button
                  type="button"
                  onClick={() => {
                    setForgotEmail(emailInput);
                    setForgotError(null);
                    setForgotSuccess(null);
                    setShowForgotModal(true);
                  }}
                  className="text-[11px] text-blue-400 hover:text-blue-300 underline font-medium"
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
                className="w-full pl-10 pr-11 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
              />
              <KeyRound className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-500" />
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
              <p className="text-[10px] text-gray-400 mt-1">
                Password must be at least 6 characters.
              </p>
            )}
          </div>

          {/* Confirm Password field (2 bar password) */}
          {authMode === "signup" && (
            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">
                Confirm Password
              </label>
              <div className="relative">
                <input
                  type={showConfirmPassword ? "text" : "password"}
                  required
                  value={confirmPasswordInput}
                  onChange={(e) => setConfirmPasswordInput(e.target.value)}
                  placeholder="Re-enter password"
                  className="w-full pl-10 pr-11 py-3 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
                />
                <KeyRound className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-500" />
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-gray-400 hover:text-white transition p-1"
                  title={showConfirmPassword ? "Hide Password" : "Show Password"}
                >
                  {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>
          )}

          {authError && (
            <div className="p-3 bg-red-950/40 border border-red-800/60 rounded-xl space-y-2.5 text-xs text-red-300">
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-red-400" />
                <span>{authError}</span>
              </div>
              {isUnverifiedEmail && authMode === "login" && (
                <div className="pt-2 border-t border-red-800/40 flex justify-end">
                  <button
                    type="button"
                    onClick={handleResendVerification}
                    disabled={resendCooldown > 0 || isResending}
                    className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 disabled:bg-gray-800 disabled:text-gray-500 text-white font-medium rounded-lg text-xs transition shadow flex items-center gap-1.5"
                  >
                    {isResending ? (
                      "Bheja ja raha hai..."
                    ) : resendCooldown > 0 ? (
                      `Dobara link bhej sakte hain (${resendCooldown}s)`
                    ) : (
                      "📨 Dobara Verification Link Bhejein"
                    )}
                  </button>
                </div>
              )}
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
            ) : (
              "Create Parent Account"
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
            <p className="text-[10px] text-gray-400 mt-1.5">
              Direct home screen install • Real-time updates
            </p>
          </div>
        )}
      </div>

      {/* Inline Forgot Password Dialog on the Login Screen itself */}
      {showForgotModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm">
          <div className="w-full max-w-sm p-6 bg-[#1B1B24] border border-[#2E2E3C] rounded-2xl shadow-2xl relative animate-in fade-in zoom-in-95">
            <button
              onClick={() => setShowForgotModal(false)}
              className="absolute top-4 right-4 p-1.5 text-gray-400 hover:text-white rounded-lg hover:bg-white/10"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="flex items-center gap-3 mb-4">
              <div className="p-2.5 bg-blue-600/20 text-blue-400 rounded-xl">
                <KeyRound className="w-5 h-5" />
              </div>
              <div>
                <h3 className="text-base font-semibold text-white">Reset Password</h3>
                <p className="text-xs text-gray-400">Receive password reset link</p>
              </div>
            </div>

            <form onSubmit={handleSendResetLink} className="space-y-3.5">
              <div>
                <label className="block text-xs font-medium text-gray-300 mb-1">
                  Registered Email Address
                </label>
                <div className="relative">
                  <input
                    type="email"
                    required
                    value={forgotEmail}
                    onChange={(e) => setForgotEmail(e.target.value)}
                    placeholder="parent@example.com"
                    className="w-full pl-10 pr-4 py-2.5 bg-[#141418] border border-[#333340] rounded-xl text-white placeholder-gray-500 focus:outline-none focus:border-blue-500 text-sm"
                  />
                  <Mail className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-gray-500" />
                </div>
              </div>

              {forgotError && (
                <div className="p-2.5 bg-red-950/40 border border-red-800/60 rounded-xl flex items-center gap-2 text-xs text-red-300">
                  <AlertTriangle className="w-3.5 h-3.5 shrink-0 text-red-400" />
                  <span>{forgotError}</span>
                </div>
              )}

              {forgotSuccess && (
                <div className="p-2.5 bg-emerald-950/40 border border-emerald-800/60 rounded-xl flex items-center gap-2 text-xs text-emerald-300">
                  <CheckCircle className="w-3.5 h-3.5 shrink-0 text-emerald-400" />
                  <span>{forgotSuccess}</span>
                </div>
              )}

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setShowForgotModal(false)}
                  className="flex-1 py-2.5 bg-gray-800 hover:bg-gray-700 text-gray-300 font-medium rounded-xl text-xs transition"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={forgotLoading}
                  className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded-xl text-xs transition flex items-center justify-center gap-2 shadow-md shadow-blue-600/30"
                >
                  {forgotLoading ? (
                    <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  ) : (
                    "Send Reset Link"
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </main>
  );
}
