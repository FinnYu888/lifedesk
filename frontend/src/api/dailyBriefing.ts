import { http } from "@/utils/http";

/** GitHub 热门项目 */
export interface GithubRepo {
  fullName: string;
  description: string;
  stars: number;
  url: string;
  language: string;
  avatar: string;
}

/** NBA 热点资讯 */
export interface NbaNewsItem {
  title: string;
  url: string;
}

/** 头条热榜 */
export interface ToutiaoItem {
  title: string;
  url: string;
  hotValue: string;
}

export type GithubPeriod = "day" | "week" | "month";

/** GitHub 热门 Top 10（period=过去24小时/一周/30天） */
export const fetchGithubTrending = (period: GithubPeriod) =>
  http.get<any, Result>("/daily/github", { params: { period }, cache: "no-store" });

/** NBA 热点资讯（前 15 条） */
export const fetchNbaNews = () =>
  http.get<any, Result>("/daily/nba", { cache: "no-store" });

/** 今日头条热榜（前 15 条） */
export const fetchToutiaoHot = () =>
  http.get<any, Result>("/daily/toutiao", { cache: "no-store" });
