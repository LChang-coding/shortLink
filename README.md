体验地址：
http://link.lcode.top/
接口测试：
在我的本机进行压测：
    机器搭载了 24 核的 i9 处理器和 32GB 内存。
    

分页查询分组的短链接接口：

项目最终实现了在每秒 2000+ 请求的高并发压力下，保持 0% 异常率与 347ms 稳定响应

<img width="2325" height="140" alt="download" src="https://github.com/user-attachments/assets/f114ef42-ca67-4b0d-9f42-8cdffe375c10" />
创建短链接接口：


<img width="2319" height="124" alt="download" src="https://github.com/user-attachments/assets/91e17ad7-05c8-470e-84ad-9eb8c4d51c9b" />
短链接创建接口成功扛住了每秒 1000+ 请求的高压，并在 3.2 万次样本测试中将响应速度稳定在 156ms

跳转短链接接口测试：

<img width="1514" height="115" alt="download" src="https://github.com/user-attachments/assets/66445b65-a115-49a4-8ab7-357311d8ec24" />

访问跳转接口在 6896.6/sec 的超高吞吐量下，依然保持了 5ms 的极速响应和 0% 的零异常率


短链接系统架构图：
<img width="2923" height="1627" alt="image" src="https://github.com/user-attachments/assets/7ea0d80b-5ad2-45e9-85f8-21ccd5c85011" />
数据库设计：
<img width="2726" height="1778" alt="image" src="https://github.com/user-attachments/assets/54c8ca93-2572-445a-8761-abc3632c76d8" />

核心链路：
<img width="2174" height="1021" alt="image" src="https://github.com/user-attachments/assets/55d90eb7-a285-4af9-9f4f-66bf37fc6454" />

<img width="1996" height="1097" alt="image" src="https://github.com/user-attachments/assets/bea3117b-46d9-488a-b643-5c6fa14df23b" />

<img width="2158" height="961" alt="image" src="https://github.com/user-attachments/assets/dba0ea72-e503-4811-b178-13c475bc5e1a" />


 
