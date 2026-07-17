"use client";

import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Check, CreditCard, AlertCircle, Loader2, RefreshCw, XCircle } from "lucide-react";
import { apiFetch } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { formatDate } from "@/lib/utils";

interface Plan {
  id: string;
  name: string;
  slug: string;
  price_cents: number;
  currency: string;
  interval: string;
  features: string;
  is_active: boolean;
}

interface MySubscription {
  active: boolean;
  subscription?: {
    id: string;
    plan_id: string;
    plan_name: string;
    status: string;
    expires_at: string;
  };
}

interface Invoice {
  id: string;
  amount_cents: number;
  currency: string;
  status: string;
  payment_method: string;
  payment_gateway: string;
  ref_id: number | null;
  paid_at: string | null;
  created_at: string;
}

interface SubscribeResponse {
  gateway_url: string;
  authority: string;
  invoice_id: string;
}

function formatPrice(cents: number, currency: string): string {
  if (currency === "IRR") {
    return cents.toLocaleString("en-US") + " IRR";
  }
  return `$${(cents / 100).toFixed(2)} ${currency}`;
}

function formatInterval(interval: string): string {
  switch (interval) {
    case "monthly": return "/mo";
    case "yearly": return "/yr";
    default: return "";
  }
}

function parseFeatures(features: string): string[] {
  try {
    const parsed = JSON.parse(features);
    if (Array.isArray(parsed)) return parsed;
  } catch {}
  return ["Ad-free reading", "Premium content", "Priority support"];
}

export default function BillingPage() {
  const queryClient = useQueryClient();
  const [subscribing, setSubscribing] = useState<string | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const [resultMessage, setResultMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const { data: plansRes, isLoading: plansLoading } = useQuery({
    queryKey: ["billing-plans"],
    queryFn: () => apiFetch<{ data: Plan[] }>("/api/billing/plans"),
  });

  const { data: subRes, isLoading: subLoading } = useQuery({
    queryKey: ["my-subscription"],
    queryFn: () => apiFetch<MySubscription>("/api/billing/my-subscription"),
  });

  const { data: invoicesRes, isLoading: invoicesLoading } = useQuery({
    queryKey: ["my-invoices"],
    queryFn: () => apiFetch<{ data: Invoice[] }>("/api/billing/invoices"),
    enabled: true,
  });

  const plans = plansRes?.data ?? [];
  const subscription = subRes?.subscription;
  const isActive = subRes?.active ?? false;
  const invoices = invoicesRes?.data ?? [];

  const handleSubscribe = async (planSlug: string) => {
    setSubscribing(planSlug);
    setResultMessage(null);
    try {
      const res = await apiFetch<SubscribeResponse>("/api/billing/subscribe", {
        method: "POST",
        body: JSON.stringify({ plan_slug: planSlug }),
      });
      if (res.gateway_url) {
        window.location.href = res.gateway_url;
      }
    } catch (e) {
      setResultMessage({
        type: "error",
        text: e instanceof Error ? e.message : "Subscription failed",
      });
    } finally {
      setSubscribing(null);
    }
  };

  const handleCancel = async () => {
    if (!confirm("Cancel your subscription? It will remain active until the end of the billing period.")) return;
    setCancelling(true);
    try {
      await apiFetch("/api/billing/subscription", { method: "DELETE" });
      queryClient.invalidateQueries({ queryKey: ["my-subscription"] });
      setResultMessage({ type: "success", text: "Subscription cancelled." });
    } catch (e) {
      setResultMessage({
        type: "error",
        text: e instanceof Error ? e.message : "Cancellation failed",
      });
    } finally {
      setCancelling(false);
    }
  };

  const urlParams = typeof window !== "undefined" ? new URLSearchParams(window.location.search) : null;
  const urlStatus = urlParams?.get("status");
  const urlRefId = urlParams?.get("ref_id");

  if (urlStatus === "success" && urlRefId) {
    setTimeout(() => {
      queryClient.invalidateQueries({ queryKey: ["my-subscription"] });
      queryClient.invalidateQueries({ queryKey: ["my-invoices"] });
    }, 500);
  }

  return (
    <div className="max-w-2xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <CreditCard className="h-6 w-6" />
        <h1 className="text-2xl font-bold">Billing & Subscription</h1>
      </div>

      {urlStatus === "success" && (
        <div className="flex items-center gap-2 p-4 rounded-lg mb-6 bg-green-50 text-green-700 border border-green-200">
          <Check className="h-5 w-5 shrink-0" />
          <div>
            <p className="font-medium">Payment successful!</p>
            <p className="text-sm">Ref ID: {urlRefId}. Your subscription is now active.</p>
          </div>
        </div>
      )}

      {urlStatus === "failed" && (
        <div className="flex items-center gap-2 p-4 rounded-lg mb-6 bg-red-50 text-red-700 border border-red-200">
          <XCircle className="h-5 w-5 shrink-0" />
          <div>
            <p className="font-medium">Payment failed or cancelled.</p>
            <p className="text-sm">Please try again or contact support.</p>
          </div>
        </div>
      )}

      {resultMessage && (
        <div
          className={`flex items-center gap-2 p-4 rounded-lg mb-6 ${
            resultMessage.type === "success"
              ? "bg-green-50 text-green-700 border border-green-200"
              : "bg-red-50 text-red-700 border border-red-200"
          }`}
        >
          {resultMessage.type === "success" ? (
            <Check className="h-5 w-5 shrink-0" />
          ) : (
            <AlertCircle className="h-5 w-5 shrink-0" />
          )}
          {resultMessage.text}
        </div>
      )}

      {isActive && subscription && (
        <div className="bg-primary/10 border border-primary/20 rounded-xl p-6 mb-8">
          <div className="flex items-center gap-2 mb-2">
            <Check className="h-5 w-5 text-primary" />
            <h3 className="font-semibold text-lg">Active Subscription</h3>
          </div>
          <p className="text-xl font-bold mb-1">{subscription.plan_name}</p>
          {subscription.expires_at && (
            <p className="text-sm text-muted-foreground">
              Expires {formatDate(subscription.expires_at)}
            </p>
          )}
          <div className="mt-4">
            <Button variant="outline" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? (
                <><Loader2 className="h-4 w-4 animate-spin mr-1" /> Cancelling...</>
              ) : (
                "Cancel Subscription"
              )}
            </Button>
          </div>
        </div>
      )}

      <h2 className="text-lg font-semibold mb-4">
        {isActive ? "Change Plan" : "Choose a Plan"}
      </h2>

      {plansLoading || subLoading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <div key={i} className="animate-pulse bg-muted rounded-xl h-40" />
          ))}
        </div>
      ) : (
        <div className="space-y-4 mb-8">
          {plans.map((plan) => {
            const currentPlan = subscription?.plan_id === plan.id;
            const features = parseFeatures(plan.features);
            return (
              <div
                key={plan.id}
                className={`border rounded-xl p-6 ${
                  currentPlan ? "border-primary bg-primary/5" : ""
                }`}
              >
                <div className="flex items-start justify-between mb-4">
                  <div>
                    <h3 className="font-semibold text-lg">{plan.name}</h3>
                    <p className="text-sm text-muted-foreground">
                      {plan.interval.charAt(0).toUpperCase() + plan.interval.slice(1)} plan
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-xl font-bold">
                      {plan.price_cents === 0
                        ? "Free"
                        : formatPrice(plan.price_cents, plan.currency)}
                    </p>
                    {plan.price_cents > 0 && (
                      <p className="text-xs text-muted-foreground">
                        {formatInterval(plan.interval)}
                      </p>
                    )}
                  </div>
                </div>

                <ul className="space-y-2 mb-4">
                  {features.map((f, i) => (
                    <li key={i} className="flex items-center gap-2 text-sm">
                      <Check className="h-4 w-4 text-primary shrink-0" />
                      {f}
                    </li>
                  ))}
                </ul>

                <Button
                  className="w-full"
                  variant={currentPlan ? "outline" : "default"}
                  disabled={currentPlan || subscribing !== null}
                  onClick={() => handleSubscribe(plan.slug)}
                >
                  {currentPlan
                    ? "Current Plan"
                    : subscribing === plan.slug
                      ? <><Loader2 className="h-4 w-4 animate-spin mr-1" /> Redirecting...</>
                      : plan.price_cents === 0
                        ? "Get Started Free"
                        : `Subscribe ${formatInterval(plan.interval)}`}
                </Button>
              </div>
            );
          })}
        </div>
      )}

      <h2 className="text-lg font-semibold mb-4">Billing History</h2>
      {invoicesLoading ? (
        <div className="animate-pulse space-y-2">
          {[1, 2].map((i) => (
            <div key={i} className="h-16 bg-muted rounded-lg" />
          ))}
        </div>
      ) : invoices.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground border rounded-xl">
          <CreditCard className="h-8 w-8 mx-auto mb-2 opacity-50" />
          <p>No invoices yet</p>
        </div>
      ) : (
        <div className="border rounded-xl divide-y">
          {invoices.map((inv) => (
            <div key={inv.id} className="flex items-center justify-between p-4">
              <div className="flex items-center gap-3">
                <div
                  className={`w-2 h-2 rounded-full ${
                    inv.status === "paid" ? "bg-green-500" : "bg-yellow-500"
                  }`}
                />
                <div>
                  <p className="font-medium">
                    {formatPrice(inv.amount_cents, inv.currency)}
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {inv.payment_gateway || inv.payment_method} · {formatDate(inv.created_at)}
                  </p>
                </div>
              </div>
              <div className="text-right">
                <span
                  className={`text-xs font-medium px-2 py-1 rounded-full ${
                    inv.status === "paid"
                      ? "bg-green-100 text-green-700"
                      : "bg-yellow-100 text-yellow-700"
                  }`}
                >
                  {inv.status === "paid" ? "Paid" : "Pending"}
                </span>
                {inv.ref_id && (
                  <p className="text-xs text-muted-foreground mt-1">Ref: {inv.ref_id}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
