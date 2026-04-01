"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChatInput } from "./ChatInput";
import { MessageBubble } from "./MessageBubble";
import { DataCards } from "./DataCards";
import { motion } from "framer-motion";
import { useVirtualizer } from "@tanstack/react-virtual";
import { estimateMessageRowHeight } from "@/lib/textMeasure";

export interface Message {
  id: string;
  role: "user" | "ai";
  content: string;
}

interface HistoryMessage {
  type?: string;
  text?: string;
  content?: string;
}

export function ChatArea({ 
  currentSessionId, 
  onChatUpdate 
}: { 
  currentSessionId: number,
  onChatUpdate?: () => void
}) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isMdViewport, setIsMdViewport] = useState(false);
  const [listWidth, setListWidth] = useState(0);
  const messageListRef = useRef<HTMLDivElement>(null);
  const isNearBottomRef = useRef(true);
  const scrollFrameRef = useRef<number | null>(null);
  const sizeCacheRef = useRef<Map<string, number>>(new Map());

  const maxBubbleWidthPx = useMemo(() => {
    const containerWidth = listWidth || 720;
    const ratio = isMdViewport ? 0.75 : 0.85;
    return Math.max(220, Math.floor(containerWidth * ratio));
  }, [isMdViewport, listWidth]);

  const scrollToBottom = useCallback((behavior: ScrollBehavior = "auto") => {
    const el = messageListRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior });
  }, []);

  const scheduleStickToBottom = useCallback(() => {
    if (!isNearBottomRef.current) return;
    if (scrollFrameRef.current !== null) {
      cancelAnimationFrame(scrollFrameRef.current);
    }
    scrollFrameRef.current = requestAnimationFrame(() => {
      scrollFrameRef.current = null;
      scrollToBottom("auto");
    });
  }, [scrollToBottom]);

  useEffect(() => {
    return () => {
      if (scrollFrameRef.current !== null) {
        cancelAnimationFrame(scrollFrameRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const mediaQuery = window.matchMedia("(min-width: 768px)");
    const handleChange = () => setIsMdViewport(mediaQuery.matches);
    handleChange();
    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, []);

  useEffect(() => {
    const el = messageListRef.current;
    if (!el) return;

    const updateWidth = () => {
      setListWidth(el.clientWidth);
    };

    updateWidth();
    const observer = new ResizeObserver(updateWidth);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const el = messageListRef.current;
    if (!el) return;

    const updateNearBottom = () => {
      const distance = el.scrollHeight - el.scrollTop - el.clientHeight;
      isNearBottomRef.current = distance < 120;
    };

    updateNearBottom();
    el.addEventListener("scroll", updateNearBottom, { passive: true });
    return () => el.removeEventListener("scroll", updateNearBottom);
  }, []);

  const estimateSize = useCallback(
    (index: number) => {
      const msg = messages[index];
      if (!msg) return 120;

      const widthBucket = Math.round(maxBubbleWidthPx / 8) * 8;
      const includesCards = msg.id === "welcome" && messages.length === 1;
      const cacheKey = `${msg.id}|${widthBucket}|${includesCards ? "cards" : "plain"}`;
      const cached = sizeCacheRef.current.get(cacheKey);
      if (cached !== undefined) {
        return cached;
      }

      const estimated = estimateMessageRowHeight(msg.content, maxBubbleWidthPx, {
        includesCards,
      });
      sizeCacheRef.current.set(cacheKey, estimated);
      return estimated;
    },
    [maxBubbleWidthPx, messages]
  );

  const rowVirtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => messageListRef.current,
    estimateSize,
    overscan: 6,
    getItemKey: (index) => messages[index]?.id ?? index,
  });

  const virtualRows = rowVirtualizer.getVirtualItems();

  useEffect(() => {
    rowVirtualizer.measure();
    scheduleStickToBottom();
  }, [messages, rowVirtualizer, scheduleStickToBottom]);

  useEffect(() => {
    sizeCacheRef.current.clear();
    rowVirtualizer.measure();
  }, [maxBubbleWidthPx, rowVirtualizer]);

  // 组件挂载或切换 session 时获取历史记录
  useEffect(() => {
    sizeCacheRef.current.clear();
    isNearBottomRef.current = true;
    setMessages([]);
    fetch(`/api/agent/history/${currentSessionId}`)
      .then((res) => {
        if (!res.ok) throw new Error("Failed to fetch history");
        return res.json();
      })
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          const historyMsgs: Message[] = data.map((m: HistoryMessage, idx: number) => ({
            id: `hist-${idx}`,
            role: m.type === "USER" ? "user" : "ai",
            content: m.text || m.content || "", 
          }));
          setMessages(historyMsgs);
        } else if (data.length === 0) {
          setMessages([
            {
              id: "welcome",
              role: "ai",
              content: "你好！我是你的 AI 助手。我们已经开始了新的对话，有什么可以帮你的吗？",
            },
          ]);
        }
        requestAnimationFrame(() => {
          scrollToBottom("auto");
        });
      })
      .catch((err) => {
        console.error("Failed to load history:", err);
      });
  }, [currentSessionId, scrollToBottom]);

  const handleSendMessage = async (content: string) => {
    if (isLoading || !content.trim()) return;
    isNearBottomRef.current = true;

    const newUserMsg: Message = { id: Date.now().toString(), role: "user", content };
    setMessages((prev) => [...prev, newUserMsg]);
    setIsLoading(true);

    const aiMsgId = (Date.now() + 1).toString();
    setMessages((prev) => [
      ...prev,
      { id: aiMsgId, role: "ai", content: "正在检索知识库并生成回答..." },
    ]);

    try {
      const response = await fetch('/api/agent/chat', {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify({ memoryId: currentSessionId, message: content }),
      });

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error("No reader available");
      const decoder = new TextDecoder("utf-8");
      
      let aiText = "";
      let buffer = "";
      let done = false;
      let streamMode: "unknown" | "sse" | "text" = "unknown";
      let flushPending = false;
      let hasReceivedContent = false;

      const flushAiText = () => {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === aiMsgId ? { ...msg, content: aiText } : msg
          )
        );
      };

      const scheduleFlush = () => {
        if (flushPending) return;
        flushPending = true;
        requestAnimationFrame(() => {
          flushPending = false;
          flushAiText();
        });
      };

      const appendSseLine = (line: string) => {
        const payload = line.substring(5).replace(/^\s/, "");
        if (payload.length > 0) {
          hasReceivedContent = true;
        }
        aiText += payload;
      };

      while (!done) {
        const { value, done: readerDone } = await reader.read();
        done = readerDone;
        if (value) {
          const chunk = decoder.decode(value, { stream: true });
          let updated = false;

          if (streamMode === "unknown") {
            const trimmed = chunk.trimStart();
            const looksLikeSse =
              trimmed.startsWith("data:") ||
              chunk.includes("\ndata:") ||
              chunk.includes("\r\ndata:");
            streamMode = looksLikeSse ? "sse" : "text";
          }

          if (streamMode === "text") {
            if (chunk.length > 0) {
              hasReceivedContent = true;
            }
            aiText += chunk;
            updated = true;
          } else {
            buffer += chunk;
            const lines = buffer.split(/\r?\n/);
            buffer = lines.pop() || "";

            for (const line of lines) {
              if (!line || line.startsWith(":")) continue;
              if (line.startsWith("data:")) {
                appendSseLine(line);
                updated = true;
              }
            }
          }

          if (updated) {
            scheduleFlush();
          }
        }
      }
      
      // 处理最后残留的数据
      if (streamMode === "sse" && buffer.trim()) {
        const lines = buffer.split(/\r?\n/);
        for (const line of lines) {
          if (!line || line.startsWith(":")) continue;
          if (line.startsWith("data:")) {
            appendSseLine(line);
          }
        }
      }
      if (streamMode === "text" && buffer) {
        hasReceivedContent = hasReceivedContent || buffer.length > 0;
        aiText += buffer;
      }
      if (hasReceivedContent) {
        flushAiText();
      }

      scheduleStickToBottom();
      
      // 当消息完整接收后，触发更新以刷新侧边栏的历史记录列表
      if (onChatUpdate) {
        onChatUpdate();
      }

    } catch (error) {
      console.error("Chat error:", error);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === aiMsgId ? { ...msg, content: "⚠️ 通讯失败，请检查后端是否正常运行。" } : msg
        )
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full w-full relative">
      <div ref={messageListRef} className="flex-1 overflow-y-auto px-4 py-6 md:px-8 pb-32">
        <div
          style={{
            height: `${rowVirtualizer.getTotalSize()}px`,
            position: "relative",
            width: "100%",
          }}
        >
          {virtualRows.map((virtualRow) => {
            const msg = messages[virtualRow.index];
            if (!msg) {
              return null;
            }

            return (
              <div
                key={virtualRow.key}
                data-index={virtualRow.index}
                ref={rowVirtualizer.measureElement}
                style={{
                  position: "absolute",
                  transform: `translateY(${virtualRow.start}px)`,
                  top: 0,
                  left: 0,
                  width: "100%",
                }}
              >
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.25 }}
                >
                  <MessageBubble message={msg} maxBubbleWidthPx={maxBubbleWidthPx} />
                  {msg.id === "welcome" && messages.length === 1 && (
                    <DataCards onSelect={(topic) => handleSendMessage(`我想测验主题：${topic}`)} />
                  )}
                </motion.div>
              </div>
            );
          })}
        </div>
      </div>
      
      <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-[#f8f9fa] via-[#f8f9fa] to-transparent pt-10 pb-6 px-4 md:px-8">
        <ChatInput onSend={handleSendMessage} />
        <div className="text-center text-[11px] text-slate-400 mt-3">
          AI generated content may be inaccurate.
        </div>
      </div>
    </div>
  );
}
