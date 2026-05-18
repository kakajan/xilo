"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { CreditCard, Save, AlertCircle, CheckCircle2, RefreshCw } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";

const settingsSchema = z.object({
  merchant_id: z.string().min(1, "Merchant ID is required"),
  sandbox: z.boolean(),
  is_active: z.boolean(),
});

type SettingsForm = z.infer<typeof settingsSchema>;

interface GatewayConfig {
  id?: string;
  gateway: string;
  merchant_id: string;
  sandbox: boolean;
  is_active: boolean;
  configured: boolean;
}

export default function PaymentSettingsPage() {
  const { user } = useAuthStore();
  const [config, setConfig] = useState<GatewayConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const form = useForm<SettingsForm>({
    resolver: zodResolver(settingsSchema),
    defaultValues: { merchant_id: "", sandbox: true, is_active: false },
  });

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {
    try {
      const data = await apiFetch<GatewayConfig>("/api/billing/settings");
      setConfig(data);
      form.reset({
        merchant_id: data.merchant_id,
        sandbox: data.sandbox,
        is_active: data.is_active,
      });
    } catch {
      setMessage({ type: "error", text: "Failed to load settings" });
    } finally {
      setLoading(false);
    }
  };

  const onSubmit = async (values: SettingsForm) => {
    setSaving(true);
    setMessage(null);
    try {
      await apiFetch("/api/billing/settings", {
        method: "PATCH",
        body: JSON.stringify(values),
      });
      setMessage({ type: "success", text: "Payment gateway settings updated" });
      loadConfig();
    } catch (e) {
      setMessage({ type: "error", text: e instanceof Error ? e.message : "Failed to save" });
    } finally {
      setSaving(false);
    }
  };

  if (user?.role !== "admin" && user?.role !== "superadmin") {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-center">
          <AlertCircle className="mx-auto h-12 w-12 text-destructive mb-4" />
          <p className="text-muted-foreground">Access denied</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <CreditCard className="h-6 w-6" />
        <h1 className="text-2xl font-bold">Payment Gateway Settings</h1>
      </div>

      {loading ? (
        <div className="animate-pulse space-y-4">
          <div className="h-10 bg-muted rounded-lg" />
          <div className="h-10 bg-muted rounded-lg" />
          <div className="h-10 bg-muted rounded-lg" />
        </div>
      ) : (
        <>
          <div className="bg-card border rounded-xl p-6 mb-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="font-semibold">Zarinpal Gateway</h3>
                <p className="text-sm text-muted-foreground mt-1">
                  Iranian payment gateway integration
                </p>
              </div>
              <div className="flex items-center gap-2">
                {config?.configured ? (
                  <span className="inline-flex items-center gap-1 text-sm text-green-600">
                    <CheckCircle2 className="h-4 w-4" /> Configured
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-sm text-yellow-600">
                    <AlertCircle className="h-4 w-4" /> Not configured
                  </span>
                )}
              </div>
            </div>
          </div>

          {message && (
            <div
              className={`flex items-center gap-2 p-4 rounded-lg mb-6 ${
                message.type === "success"
                  ? "bg-green-50 text-green-700 border border-green-200"
                  : "bg-red-50 text-red-700 border border-red-200"
              }`}
            >
              {message.type === "success" ? (
                <CheckCircle2 className="h-5 w-5 shrink-0" />
              ) : (
                <AlertCircle className="h-5 w-5 shrink-0" />
              )}
              {message.text}
            </div>
          )}

          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            <div>
              <label className="block text-sm font-medium mb-2">
                Merchant ID (36-character code)
              </label>
              <input
                {...form.register("merchant_id")}
                type="text"
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                className="w-full px-4 py-2.5 rounded-lg border bg-background text-sm font-mono"
                maxLength={36}
              />
              {form.formState.errors.merchant_id && (
                <p className="text-sm text-destructive mt-1">
                  {form.formState.errors.merchant_id.message}
                </p>
              )}
              <p className="text-xs text-muted-foreground mt-1">
                Obtain from your Zarinpal dashboard at zarinpal.com
              </p>
            </div>

            <div className="flex items-center justify-between p-4 border rounded-lg">
              <div>
                <label className="font-medium">Sandbox Mode</label>
                <p className="text-sm text-muted-foreground mt-0.5">
                  Test payments without real transactions
                </p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={form.watch("sandbox")}
                onClick={() => form.setValue("sandbox", !form.watch("sandbox"))}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                  form.watch("sandbox") ? "bg-primary" : "bg-muted"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-background shadow ring-0 transition-transform ${
                    form.watch("sandbox") ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center justify-between p-4 border rounded-lg">
              <div>
                <label className="font-medium">Gateway Active</label>
                <p className="text-sm text-muted-foreground mt-0.5">
                  Enable payment processing for users
                </p>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={form.watch("is_active")}
                onClick={() => form.setValue("is_active", !form.watch("is_active"))}
                className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                  form.watch("is_active") ? "bg-primary" : "bg-muted"
                }`}
              >
                <span
                  className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-background shadow ring-0 transition-transform ${
                    form.watch("is_active") ? "translate-x-5" : "translate-x-0"
                  }`}
                />
              </button>
            </div>

            <div className="flex items-center gap-3">
              <Button type="submit" disabled={saving}>
                {saving ? (
                  <>
                    <RefreshCw className="h-4 w-4 animate-spin" /> Saving...
                  </>
                ) : (
                  <>
                    <Save className="h-4 w-4" /> Save Settings
                  </>
                )}
              </Button>
              <Button type="button" variant="outline" onClick={loadConfig}>
                Reset
              </Button>
            </div>
          </form>
        </>
      )}
    </div>
  );
}
