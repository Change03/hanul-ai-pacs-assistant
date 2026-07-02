"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import {
  Activity,
  Archive,
  BrainCircuit,
  ClipboardCheck,
  Database,
  FileClock,
  LayoutDashboard,
  LogOut,
  Network,
  ShieldAlert
} from "lucide-react";
import { apiFetch } from "@/lib/api";

type Me = { username: string; role: string; authenticated: boolean };

const nav = [
  { href: "/", label: "대시보드", icon: LayoutDashboard },
  { href: "/studies", label: "검사 목록", icon: Archive },
  { href: "/qc", label: "QC 게이트", icon: ClipboardCheck },
  { href: "/ai/jobs", label: "AI 작업", icon: BrainCircuit },
  { href: "/audit", label: "감사 로그", icon: FileClock },
  { href: "/architecture", label: "아키텍처", icon: Network }
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<Me | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const pathname = usePathname();

  useEffect(() => {
    apiFetch<Me>("/api/auth/me")
      .then(setMe)
      .catch(() => setMe({ username: "", role: "", authenticated: false }))
      .finally(() => setLoading(false));
  }, []);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    const data = new FormData(event.currentTarget);
    try {
      const result = await apiFetch<Me>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: data.get("username"), password: data.get("password") })
      });
      setMe(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "로그인에 실패했습니다");
    }
  }

  async function logout() {
    await apiFetch<Me>("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    setMe({ username: "", role: "", authenticated: false });
  }

  if (loading) {
    return <div className="min-h-screen p-8 text-slate-300">Hanul AI-PACS를 불러오는 중...</div>;
  }

  if (!me?.authenticated) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-[#08111f] p-6">
        <section className="medical-card w-full max-w-md p-6 shadow-glow">
          <div className="mb-5 flex items-center gap-3">
            <div className="rounded-md bg-teal-300/10 p-3 text-cyanSoft">
              <BrainCircuit size={28} />
            </div>
            <div>
              <h1 className="text-xl font-semibold">Hanul AI-PACS Assistant</h1>
              <p className="text-sm text-slate-400">로컬 데모 로그인</p>
            </div>
          </div>
          <form onSubmit={login} className="space-y-4">
            <label className="block text-sm text-slate-300">
              사용자 이름
              <input name="username" defaultValue="demo" className="mt-2 w-full rounded-md border border-line bg-[#0b1322] px-3 py-2 outline-none focus:border-cyanSoft" />
            </label>
            <label className="block text-sm text-slate-300">
              비밀번호
              <input name="password" type="password" defaultValue="demo" className="mt-2 w-full rounded-md border border-line bg-[#0b1322] px-3 py-2 outline-none focus:border-cyanSoft" />
            </label>
            {error && <div className="rounded-md border border-roseSoft/40 bg-rose-950/30 px-3 py-2 text-sm text-rose-100">{error}</div>}
            <button className="w-full rounded-md bg-cyanSoft px-4 py-2 font-semibold text-slate-950 hover:bg-teal-200">로그인</button>
          </form>
          <p className="mt-4 text-xs text-slate-500">데모 전용입니다. 임상 진료에 사용하지 마세요. 실제 환자 데이터는 없습니다.</p>
          <p className="mt-1 text-xs text-slate-600">Demo only. Not for clinical use. No real patient data.</p>
        </section>
      </main>
    );
  }

  return (
    <div className="min-h-screen bg-[#08111f] text-slate-100">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-line bg-[#0b1322] p-4 md:block">
        <div className="mb-7 flex items-center gap-3">
          <div className="rounded-md bg-teal-300/10 p-2 text-cyanSoft"><Database size={24} /></div>
          <div>
            <div className="font-semibold">Hanul AI-PACS</div>
            <div className="text-xs text-slate-500">DICOMweb 데모</div>
          </div>
        </div>
        <nav className="space-y-1">
          {nav.map((item) => {
            const active = pathname === item.href || (item.href !== "/" && pathname.startsWith(item.href));
            const Icon = item.icon;
            return (
              <Link key={item.href} href={item.href} className={`flex items-center gap-3 rounded-md px-3 py-2 text-sm ${active ? "bg-teal-300/10 text-cyanSoft" : "text-slate-300 hover:bg-slate-800"}`}>
                <Icon size={18} />
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="absolute bottom-4 left-4 right-4 rounded-md border border-line bg-panel p-3 text-xs text-slate-400">
          <div className="mb-2 flex items-center gap-2 text-amberSoft"><ShieldAlert size={16} /> 데모 안전 안내</div>
          <div>데모 전용입니다. 임상 진료에 사용하지 마세요. 실제 환자 데이터는 없습니다.</div>
          <div className="mt-1 text-slate-500">Demo only. Not for clinical use. No real patient data.</div>
        </div>
      </aside>
      <div className="md:pl-64">
        <header className="sticky top-0 z-20 border-b border-line bg-[#08111f]/95 px-4 py-3 backdrop-blur md:px-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-sm text-slate-300">
              <Activity size={18} className="text-cyanSoft" />
              <span className="font-medium text-slate-100">심사용 데모 모드</span>
              <span className="hidden text-slate-500 sm:inline">검사 조회 &gt; QC 실행 &gt; AI 분석 &gt; 결과 DICOM을 PACS에 저장</span>
            </div>
            <div className="flex items-center gap-3 text-sm">
              <span className="status-pill text-blueSoft">{me.username} / {me.role}</span>
              <button onClick={logout} className="rounded-md border border-line p-2 text-slate-300 hover:bg-slate-800" title="로그아웃">
                <LogOut size={16} />
              </button>
            </div>
          </div>
        </header>
        <main className="p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
