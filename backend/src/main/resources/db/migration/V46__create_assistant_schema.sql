-- Trợ lý hỗ trợ (thiết kế docs/support-chatbot/06-du-lieu-va-schema.md mục 6.1).
-- Schema riêng cho assistant-service; các bảng bên trong do Alembic của assistant-service quản lý.
-- Role assistant_app và mật khẩu được tạo ngoài migration (biến môi trường), không ghi ở đây.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE SCHEMA IF NOT EXISTS assistant;

-- unaccent() không phải IMMUTABLE nên không dùng trực tiếp trong cột sinh tự động / chỉ mục được.
-- Bọc trong hàm IMMUTABLE với từ điển chỉ định rõ.
CREATE OR REPLACE FUNCTION assistant.f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;
