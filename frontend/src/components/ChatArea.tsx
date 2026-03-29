"use client";

import { useState, useEffect, useRef } from "react";
import { ChatInput } from "./ChatInput";
import { MessageBubble } from "./MessageBubble";
import { DataCards } from "./DataCards";
import { motion } from "framer-motion";

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
  const messageListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = messageListRef.current;
    if (!el) return;
    el.scrollTo({ top: el.scrollHeight, behavior: "smooth" });
  }, [messages]);

  // 组件挂载或切换 session 时获取历史记录
  useEffect(() => {
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
      })
      .catch((err) => {
        console.error("Failed to load history:", err);
      });
  }, [currentSessionId]);

  const handleSendMessage = async (content: string) => {
    if (isLoading || !content.trim()) return;

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
      <div ref={messageListRef} className="flex-1 overflow-y-auto px-4 py-6 md:px-8 pb-32 scroll-smooth">
        {messages.map((msg) => (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
            key={msg.id}
          >
            <MessageBubble message={msg} />
            {msg.id === "welcome" && messages.length === 1 && (
              <DataCards onSelect={(topic) => handleSendMessage(`我想测验主题：${topic}`)} />
            )}
          </motion.div>
        ))}
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
