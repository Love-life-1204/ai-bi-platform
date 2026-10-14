-- Forward-only repair namespace; require original V002 permission seeds first.
-- Preserve import:read=21 and sync:read=23, and all old grants/checksums.
-- Runner must refuse any reserved ID/code collision; no IGNORE/REPLACE/overwrite.
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,action_code) VALUES
(100,'datasource:read','读取数据源','DATASOURCE','read'),
(101,'datasource:create','创建数据源','DATASOURCE','create'),
(102,'datasource:update','修改数据源','DATASOURCE','update'),
(103,'datasource:delete','删除数据源','DATASOURCE','delete'),
(104,'datasource:test','测试数据源','DATASOURCE','test'),
(105,'file:upload','上传文件','FILE','upload'),
(106,'file:read','预览文件','FILE','read'),
(107,'file:download','下载文件','FILE','download'),
(108,'import:create','创建导入','IMPORT','create'),
(109,'import:run','执行导入','IMPORT','run'),
(110,'import:cancel','取消导入','IMPORT','cancel'),
(111,'sync:create','创建同步','SYNC','create'),
(112,'sync:update','修改同步','SYNC','update'),
(113,'sync:run','运行同步','SYNC','run'),
(114,'sync:cancel','取消同步','SYNC','cancel'),
(115,'sync:compensate','补偿同步','SYNC','compensate');
