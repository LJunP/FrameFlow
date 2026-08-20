"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useSession } from "@/lib/store";
import { Spinner } from "@/components/ui";

export default function HomePage() {
  const router = useRouter();
  const { token } = useSession();

  useEffect(() => {
    router.replace(token ? "/dashboard" : "/login");
  }, [token, router]);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <Spinner size="lg" className="text-brand-600" />
    </div>
  );
}
