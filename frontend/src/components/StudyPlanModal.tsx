"use client";

import { useEffect, useMemo, useState } from "react";
import { CalendarCheck2, Clock3, MessageCircle, Send, ShieldCheck, X } from "lucide-react";
import { cn } from "@/lib/utils";

interface StudyTaskView {
  id: number;
  planName: string;
  studyDate: string;
  reminderTime: string;
  ragTopic: string;
  studyContent?: string;
  timezone: string;
  enabled: boolean;
  sentStatus: number;
  errorMessage?: string;
  sentAt?: string;
  hasFeishuConfig: boolean;
  channels: string;
}

interface DayForm {
  date: string;
  reminderTime: string;
  ragTopic: string;
  studyContent: string;
}

interface StudyPlanModalProps {
  isOpen: boolean;
  onClose: () => void;
}

function errorMessageOf(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function toDateInput(date: Date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function getUpcomingSevenDays(): DayForm[] {
  const today = new Date();
  const rows: DayForm[] = [];
  for (let i = 0; i < 7; i += 1) {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    rows.push({
      date: toDateInput(d),
      reminderTime: "20:00",
      ragTopic: "",
      studyContent: "",
    });
  }
  return rows;
}

export function StudyPlanModal({ isOpen, onClose }: StudyPlanModalProps) {
  const [planName, setPlanName] = useState("接下来一周学习计划");
  const [days, setDays] = useState<DayForm[]>(getUpcomingSevenDays());
  const [timezone, setTimezone] = useState("Asia/Shanghai");
  const channels: string[] = ["feishu"];
  const [feishuOpenId, setFeishuOpenId] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [tasks, setTasks] = useState<StudyTaskView[]>([]);
  const [loadingTasks, setLoadingTasks] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const canSubmit = useMemo(() => {
    const hasTopic = days.some((d) => d.ragTopic.trim().length > 0);
    if (!hasTopic) return false;
    if (!feishuOpenId.trim()) return false;
    return true;
  }, [days, feishuOpenId]);

  useEffect(() => {
    if (!isOpen) return;
    setDays(getUpcomingSevenDays());
    loadTasks();
  }, [isOpen]);

  const loadTasks = async () => {
    setLoadingTasks(true);
    try {
      const res = await fetch("/api/agent/study-plan/tasks?days=14");
      if (!res.ok) throw new Error("查询失败");
      const data = await res.json();
      setTasks(Array.isArray(data) ? data : []);
    } catch {
      setMessage({ type: "error", text: "读取任务列表失败，请稍后重试" });
    } finally {
      setLoadingTasks(false);
    }
  };

  const updateDay = (index: number, key: keyof DayForm, value: string) => {
    setDays((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [key]: value };
      return next;
    });
  };

  const savePlan = async () => {
    if (!canSubmit || isSubmitting) return;
    setIsSubmitting(true);
    setMessage(null);

    try {
      const payload = {
        planName,
        days,
        channels,
        feishuOpenId,
        timezone,
      };

      const res = await fetch("/api/agent/study-plan/weekly", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.message || "保存失败");
      }
      setMessage({ type: "success", text: `保存成功，已创建 ${data.created} 条提醒任务` });
      await loadTasks();
    } catch (error: unknown) {
      setMessage({ type: "error", text: errorMessageOf(error, "保存失败") });
    } finally {
      setIsSubmitting(false);
    }
  };

  const setTaskStatus = async (taskId: number, enabled: boolean) => {
    try {
      const res = await fetch(`/api/agent/study-plan/tasks/${taskId}/status?enabled=${enabled}`, {
        method: "PATCH",
      });
      if (!res.ok) {
        const data = await res.json();
        throw new Error(data?.message || "更新状态失败");
      }
      await loadTasks();
    } catch (error: unknown) {
      setMessage({ type: "error", text: errorMessageOf(error, "更新状态失败") });
    }
  };

  const testReminder = async (taskId: number) => {
    try {
      const res = await fetch(`/api/agent/study-plan/tasks/${taskId}/test`, {
        method: "POST",
      });
      const data = await res.json();
      if (!res.ok) {
        throw new Error(data?.message || "测试发送失败");
      }
      setMessage({ type: "success", text: data?.result || "测试提醒发送成功" });
    } catch (error: unknown) {
      setMessage({ type: "error", text: errorMessageOf(error, "测试发送失败") });
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm flex items-center justify-center px-3 py-4">
      <div className="relative w-full max-w-6xl max-h-[92vh] overflow-y-auto rounded-3xl bg-white shadow-2xl border border-slate-200">
        <button
          onClick={onClose}
          className="absolute right-4 top-4 rounded-full p-2 text-slate-500 hover:bg-slate-100"
        >
          <X size={20} />
        </button>

        <div className="p-6 md:p-8 border-b border-slate-200 bg-gradient-to-r from-cyan-50 via-emerald-50 to-lime-50 rounded-t-3xl">
          <h2 className="text-2xl font-semibold text-slate-800 flex items-center gap-2">
            <CalendarCheck2 className="text-emerald-600" />
            接下来一周学习计划
          </h2>
          <p className="text-sm text-slate-600 mt-2">
            单用户模式，提醒频率为每日一次。当前仅支持飞书个人私聊提醒。
          </p>
        </div>

        <div className="p-6 md:p-8 grid grid-cols-1 lg:grid-cols-2 gap-8">
          <section className="space-y-4">
            <h3 className="text-lg font-semibold text-slate-800">计划配置</h3>

            <div>
              <label className="block text-sm text-slate-700 mb-1">计划名称</label>
              <input
                value={planName}
                onChange={(e) => setPlanName(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:ring-2 focus:ring-emerald-300"
              />
            </div>

            <div>
              <label className="block text-sm text-slate-700 mb-1">时区</label>
              <input
                value={timezone}
                onChange={(e) => setTimezone(e.target.value)}
                className="w-full rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:ring-2 focus:ring-emerald-300"
              />
            </div>

            <div className="space-y-2">
              <span className="text-sm text-slate-700">提醒渠道</span>
              <div className="flex flex-wrap gap-2">
                <div className="rounded-xl border border-emerald-500 bg-emerald-50 text-emerald-700 px-3 py-2 text-sm">
                  飞书个人私聊
                </div>
              </div>
            </div>

            {channels.includes("feishu") && (
              <div>
                <label className="block text-sm text-slate-700 mb-1">飞书 open_id</label>
                <input
                  value={feishuOpenId}
                  onChange={(e) => setFeishuOpenId(e.target.value)}
                  placeholder="ou_xxxxxxxxx"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2.5 outline-none focus:ring-2 focus:ring-emerald-300"
                />
              </div>
            )}

            <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-700 font-medium mb-3 flex items-center gap-2">
                <Clock3 size={16} />
                每日计划（从今天起未来7天）
              </div>
              <div className="space-y-2 max-h-[340px] overflow-y-auto pr-1">
                {days.map((day, index) => (
                  <div key={index} className="rounded-xl border border-slate-200 bg-white p-3 space-y-2">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
                      <input
                        type="date"
                        value={day.date}
                        onChange={(e) => updateDay(index, "date", e.target.value)}
                        className="rounded-lg border border-slate-300 px-2 py-2 text-sm"
                      />
                      <input
                        type="time"
                        value={day.reminderTime}
                        onChange={(e) => updateDay(index, "reminderTime", e.target.value)}
                        className="rounded-lg border border-slate-300 px-2 py-2 text-sm"
                      />
                      <input
                        value={day.ragTopic}
                        onChange={(e) => updateDay(index, "ragTopic", e.target.value)}
                        placeholder="RAG主题，例如向量检索"
                        className="rounded-lg border border-slate-300 px-2 py-2 text-sm"
                      />
                    </div>
                    <input
                      value={day.studyContent}
                      onChange={(e) => updateDay(index, "studyContent", e.target.value)}
                      placeholder="学习目标（可选）"
                      className="w-full rounded-lg border border-slate-300 px-2 py-2 text-sm"
                    />
                  </div>
                ))}
              </div>
            </div>

            <button
              onClick={savePlan}
              disabled={!canSubmit || isSubmitting}
              className="w-full rounded-xl bg-slate-800 text-white py-3 font-medium hover:bg-slate-700 disabled:bg-slate-300 disabled:cursor-not-allowed"
            >
              {isSubmitting ? "保存中..." : "保存周计划"}
            </button>

            {message && (
              <div
                className={cn(
                  "rounded-xl px-3 py-2 text-sm",
                  message.type === "success"
                    ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
                    : "bg-rose-50 text-rose-700 border border-rose-200"
                )}
              >
                {message.text}
              </div>
            )}
          </section>

          <section className="space-y-4">
            <h3 className="text-lg font-semibold text-slate-800 flex items-center gap-2">
              <ShieldCheck size={18} className="text-cyan-600" />
              即将执行的任务
            </h3>

            {loadingTasks ? (
              <div className="text-sm text-slate-500">正在加载任务列表...</div>
            ) : tasks.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 p-6 text-sm text-slate-500">
                暂无任务，先在左侧创建未来一周计划。
              </div>
            ) : (
              <div className="space-y-3 max-h-[620px] overflow-y-auto pr-1">
                {tasks.map((task) => (
                  <div key={task.id} className="rounded-2xl border border-slate-200 p-4 bg-white">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="font-medium text-slate-800">{task.studyDate} {task.reminderTime.slice(0, 5)}</div>
                        <div className="text-sm text-slate-600 mt-1">主题：{task.ragTopic}</div>
                        {task.studyContent && <div className="text-xs text-slate-500 mt-1">目标：{task.studyContent}</div>}
                        <div className="text-xs text-slate-500 mt-1">渠道：{task.channels}</div>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() => setTaskStatus(task.id, !task.enabled)}
                          className={cn(
                            "rounded-lg px-2.5 py-1.5 text-xs border",
                            task.enabled
                              ? "border-emerald-300 text-emerald-700 bg-emerald-50"
                              : "border-slate-300 text-slate-600"
                          )}
                        >
                          {task.enabled ? "已启用" : "已停用"}
                        </button>
                        <button
                          onClick={() => testReminder(task.id)}
                          className="rounded-lg border border-cyan-300 bg-cyan-50 text-cyan-700 px-2.5 py-1.5 text-xs flex items-center gap-1"
                        >
                          <Send size={12} /> 测试
                        </button>
                      </div>
                    </div>

                    {task.sentStatus === 1 && task.sentAt && (
                      <div className="text-xs text-emerald-600 mt-2">已发送：{task.sentAt.replace("T", " ")}</div>
                    )}
                    {task.sentStatus === 2 && task.errorMessage && (
                      <div className="text-xs text-rose-600 mt-2">失败：{task.errorMessage}</div>
                    )}
                    {!task.hasFeishuConfig && task.channels.includes("feishu") && (
                      <div className="text-xs text-amber-600 mt-2 flex items-center gap-1">
                        <MessageCircle size={12} /> 飞书参数未配置完整
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
