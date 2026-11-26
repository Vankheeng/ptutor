-- H1: "Xoá lịch sử gợi ý". Hồ sơ sở thích được tính lại từ hành vi 180 ngày gần nhất, nên xoá dòng hồ sơ
-- là chưa đủ: lần tính lại kế tiếp sẽ dựng lại y nguyên. Mốc này đánh dấu thời điểm người dùng yêu cầu xoá;
-- mọi tín hiệu trước mốc bị bỏ qua.
ALTER TABLE recommendation_user_profiles
    ADD COLUMN history_reset_at timestamp;
