import { http } from "@/utils/http";

/** 曲目（来源：link=外链 / upload=上传源文件） */
export interface MusicTrack {
  id: number;
  title: string;
  artist?: string;
  sourceType: "link" | "upload";
  url?: string;
  filePath?: string;
  fileSize?: number;
  durationSec?: number;
  createTime?: string;
}

export const fetchMusicList = () => http.get<any, Result>("/music/list");
export const addMusicLink = (data: { title: string; artist?: string; url: string }) =>
  http.post<any, Result>("/music", data);
export const deleteMusic = (id: number) => http.delete<any, Result>(`/music/${id}`);
/** 回填时长（秒） */
export const updateMusicDuration = (id: number, durationSec: number) =>
  http.post<any, Result>(`/music/${id}/duration`, { durationSec });

/**
 * 上传音乐源文件（multipart）。
 * 返回新建的曲目记录。
 */
export async function uploadMusicFile(file: File, title?: string, artist?: string): Promise<MusicTrack> {
  const fd = new FormData();
  fd.append("file", file);
  if (title) fd.append("title", title);
  if (artist) fd.append("artist", artist);
  // 认证：fetch 不走 axios 拦截器，手动附带 Bearer token
  const token = localStorage.getItem("lifedesk-token") || "";
  const resp = await fetch("/api/music/upload", {
    method: "POST",
    body: fd,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  });
  if (!resp.ok) {
    let msg = `HTTP ${resp.status}`;
    try {
      const j = await resp.json();
      msg = j?.msg || msg;
    } catch { /* ignore */ }
    throw new Error(msg);
  }
  const j = await resp.json();
  return j?.data;
}

/** 曲目的可播放地址（外链用原地址；上传走后端流式接口） */
export function trackSrc(t: Pick<MusicTrack, "sourceType" | "url" | "id">): string {
  if (t.sourceType !== "upload") return t.url || "";
  // <audio> 元素无法携带 Authorization 头，用查询参数传 token（后端拦截器支持）
  const token = localStorage.getItem("lifedesk-token") || "";
  return `/api/music/${t.id}/file?token=${encodeURIComponent(token)}`;
}
