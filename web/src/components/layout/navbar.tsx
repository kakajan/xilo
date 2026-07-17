"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Search, PlusCircle, Bell, LogIn, UserPlus, LayoutDashboard, Settings } from "lucide-react";
import { useAuthStore } from "@/stores/auth-store";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { getInitials } from "@/lib/utils";

export function Navbar() {
  const { user, isAuthenticated, logout } = useAuthStore();
  const router = useRouter();
  const [search, setSearch] = useState("");

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (search.trim()) {
      router.push(`/search?q=${encodeURIComponent(search.trim())}`);
    }
  };

  return (
    <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur">
      <div className="max-w-4xl mx-auto flex items-center justify-between px-4 h-14">
        <Link href="/" className="font-bold text-xl text-primary">
          Xilo
        </Link>

        <form onSubmit={handleSearch} className="hidden sm:flex items-center gap-2 flex-1 max-w-sm mx-4">
          <div className="relative w-full">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2 rounded-lg border bg-background text-sm"
            />
          </div>
        </form>

        <div className="flex items-center gap-2">
          {isAuthenticated ? (
            <>
              {(user?.role === "admin" || user?.role === "superadmin") && (
                <Button variant="ghost" size="icon" onClick={() => router.push("/dashboard")}>
                  <LayoutDashboard className="h-5 w-5" />
                </Button>
              )}
              <Button variant="ghost" size="icon" onClick={() => router.push("/write")}>
                <PlusCircle className="h-5 w-5" />
              </Button>
              <Button variant="ghost" size="icon" onClick={() => router.push("/notifications")}>
                <Bell className="h-5 w-5" />
              </Button>
              <Button variant="ghost" size="icon" onClick={() => router.push("/settings")}>
                <Settings className="h-5 w-5" />
              </Button>
              <Button variant="ghost" size="icon" onClick={() => router.push(`/${user?.username}`)}>
                <Avatar className="h-8 w-8">
                  <AvatarFallback>{user ? getInitials(user.display_name || user.username) : "?"}</AvatarFallback>
                </Avatar>
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" onClick={() => router.push("/login")}>
                <LogIn className="h-4 w-4 mr-1" /> Sign in
              </Button>
              <Button size="sm" onClick={() => router.push("/register")}>
                <UserPlus className="h-4 w-4 mr-1" /> Sign up
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
