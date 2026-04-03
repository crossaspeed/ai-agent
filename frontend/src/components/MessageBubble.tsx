"use client";

import { Message } from "./ChatArea";
import { cn } from "@/lib/utils";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneLight } from "react-syntax-highlighter/dist/esm/styles/prism";

export function MessageBubble({
  message,
  maxBubbleWidthPx: _maxBubbleWidthPx,
}: {
  message: Message;
  maxBubbleWidthPx?: number;
}) {
  const isUser = message.role === "user";

  return (
    <div className="w-full">
      <div
        className={cn(
          "mb-1 px-1 text-[11px] font-medium tracking-wide",
          isUser ? "text-slate-500" : "text-slate-400"
        )}
      >
        {isUser ? "你" : "AI 助手"}
      </div>

      <div
        className={cn(
          "w-full text-[var(--chat-font-size)] leading-[var(--chat-line-height)]",
          isUser
            ? "rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-slate-800 md:px-5"
            : "px-1 py-1 text-slate-800"
        )}
      >
        <div className={cn("prose max-w-none break-words", "prose-slate")}> 
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              code(props) {
                const { children, className, ...rest } = props;
                const match = /language-(\w+)/.exec(className || "");
                return match ? (
                  <div className="rounded-xl overflow-hidden my-3 border border-slate-200">
                    <div className="bg-slate-100 text-xs px-3 py-1.5 text-slate-500 font-mono flex justify-between items-center">
                      {match[1]}
                    </div>
                    <SyntaxHighlighter
                      style={oneLight}
                      language={match[1]}
                      PreTag="div"
                      customStyle={{ margin: 0, padding: '1rem', background: '#fafafa', fontSize: '13px' }}
                    >
                      {String(children).replace(/\n$/, "")}
                    </SyntaxHighlighter>
                  </div>
                ) : (
                  <code {...rest} className={cn("bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md font-mono text-[13px]", className)}>
                    {children}
                  </code>
                );
              },
              p: ({ children }) => <p className="mb-3 last:mb-0 leading-8">{children}</p>,
            }}
          >
            {message.content}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  );
}
