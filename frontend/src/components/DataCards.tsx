import { motion } from "framer-motion";
import { useCallback, useEffect, useState } from "react";

export interface Topic {
  id: string;
  name: string;
  docCount: number;
}

interface TopicPageResponse {
  items: Topic[];
  page: number;
  size: number;
  hasNext: boolean;
}

const TOPICS_PAGE_SIZE = 20;

export function DataCards({ onSelect }: { onSelect: (topic: string) => void }) {
  const [topics, setTopics] = useState<Topic[]>([]);
  const [page, setPage] = useState(1);
  const [hasNext, setHasNext] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadTopics = useCallback((targetPage: number, append: boolean) => {
    return fetch(`/api/knowledge/topics?page=${targetPage}&size=${TOPICS_PAGE_SIZE}`)
      .then(res => res.json())
      .then((data: TopicPageResponse | Topic[]) => {
        if (Array.isArray(data)) {
          setTopics(data);
          setPage(1);
          setHasNext(false);
          return;
        }
        const items = Array.isArray(data?.items) ? data.items : [];
        setTopics(prev => (append ? [...prev, ...items] : items));
        setPage(data?.page ?? targetPage);
        setHasNext(Boolean(data?.hasNext));
      });
  }, []);

  useEffect(() => {
    loadTopics(1, false)
      .then(() => setLoading(false))
      .catch(err => {
        console.error("Failed to load topics:", err);
        setLoading(false);
      });
  }, [loadTopics]);

  const handleLoadMore = () => {
    setIsLoadingMore(true);
    loadTopics(page + 1, true)
      .catch(err => console.error("Failed to load more topics:", err))
      .finally(() => setIsLoadingMore(false));
  };

  if (loading) return null;
  if (topics.length === 0) return null;

  return (
    <div className="mt-8 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {topics.map((t, i) => (
        <motion.div
          key={t.id || t.name}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: i * 0.1 }}
          onClick={() => onSelect(t.name)}
          className="bg-white p-5 rounded-2xl shadow-sm border border-slate-100 hover:border-blue-200 hover:shadow-md cursor-pointer transition-all flex flex-col items-start justify-between"
        >
          <div className="flex items-center gap-3 w-full">
            <div className="w-10 h-10 rounded-full bg-blue-50 text-blue-600 flex items-center justify-center text-xl shrink-0">
              📚
            </div>
            <div className="flex-1 truncate">
              <h3 className="font-semibold text-slate-800 text-base truncate">{t.name}</h3>
              <p className="text-xs text-slate-500 mt-0.5">{t.docCount} 份文档</p>
            </div>
          </div>
          <div className="mt-4 text-sm text-slate-600 flex justify-between w-full items-center">
            <span>开始测验</span>
            <span className="text-blue-500">&rarr;</span>
          </div>
        </motion.div>
      ))}
      {hasNext && (
        <button
          type="button"
          onClick={handleLoadMore}
          disabled={isLoadingMore}
          className="col-span-full mt-2 p-3 rounded-2xl border border-slate-200 bg-white hover:bg-slate-50 text-sm text-slate-600 disabled:opacity-60"
        >
          {isLoadingMore ? "加载中..." : "加载更多主题"}
        </button>
      )}
    </div>
  );
}
