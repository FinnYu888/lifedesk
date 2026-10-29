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
  lyrics?: string;
  createTime?: string;
}

export const fetchMusicList = () => http.get<any, Result>("/music/list");
export const addMusicLink = (data: { title: string; artist?: string; url: string }) =>
  http.post<any, Result>("/music", data);
export const deleteMusic = (id: number) => http.delete<any, Result>(`/music/${id}`);
/** 回填时长（秒） */
export const updateMusicDuration = (id: number, durationSec: number) =>
  http.post<any, Result>(`/music/${id}/duration`, { durationSec });

/** 拉取歌词：优先已存，否则从 LRCLIB 搜索入库，返回 {lyrics, synced} */
export const fetchMusicLyrics = (id: number) => http.get<any, Result>(`/music/${id}/lyrics`);

/** 手动保存/编辑歌词 */
export const saveMusicLyrics = (id: number, lyrics: string) =>
  http.post<any, Result>(`/music/${id}/lyrics`, { lyrics });

/**
 * 上传音乐源文件（multipart）。
 * 返回新建的曲目记录。
 */
export async function uploadMusicFile(file: File, title?: string, artist?: string): Promise<MusicTrack> {
  const fd = new FormData();
  fd.append("file", file);
  if (title) fd.append("title", title);
  if (artist) fd.append("artist", artist);
  const j = await postMultipart("/music/upload", fd);
  return j?.data;
}

/**
 * 替换曲目音频源文件（保留歌名/歌手，仅更换音频文件）。
 * 用于把占位/外链曲目升级为完整歌曲。
 */
export async function replaceMusicFile(id: number, file: File): Promise<MusicTrack> {
  const fd = new FormData();
  fd.append("file", file);
  const j = await postMultipart(`/music/${id}/file`, fd);
  return j?.data;
}

/** multipart 上传通用：fetch 不走 axios 拦截器，手动附带 Bearer token */
async function postMultipart(path: string, fd: FormData): Promise<any> {
  const token = localStorage.getItem("lifedesk-token") || "";
  const resp = await fetch(`/api${path}`, {
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
  return resp.json();
}

/**
 * 曲目的可播放地址：统一走后端流式代理。
 * - 外链：后端 /music/{id}/file 服务端转发外部音频（浏览器不直连外部源，
 *   规避本机网络代理/拦截导致的加载失败）；
 * - 上传：同一接口输出本地文件。
 * <audio> 元素无法携带 Authorization 头，用查询参数传 token（后端拦截器支持）。
 */
export function trackSrc(t: Pick<MusicTrack, "sourceType" | "url" | "id">): string {
  const token = localStorage.getItem("lifedesk-token") || "";
  return `/api/music/${t.id}/file?token=${encodeURIComponent(token)}`;
}
