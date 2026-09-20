import { useState } from 'react';
import './HomeLanding.css';
import logo from '../../assets/ptutor-logo.svg';
import { Icon } from '../ui/Icon';
import { useHomeLanding } from '../../hooks/useHomeLanding';
import { useHomeReveal } from '../../hooks/useHomeReveal';
import { useScrollToTop } from '../../hooks/useScrollToTop';
import { HomeNavigation } from './HomeNavigation';
import { ReviewCard } from './ReviewCard';
import { SubjectCard } from './SubjectCard';
import { TeachingPostCard } from './TeachingPostCard';
import { HOME_FEATURES } from './homeContent';

export function HomeLanding() {
  useHomeReveal();
  const [menuOpen, setMenuOpen] = useState(false);
  const { isVisible: isBackToTopVisible, scrollToTop } = useScrollToTop();
  const {
    subjects,
    grades,
    posts,
    reviews,
    visibleSubjects,
    currentSubjectPage,
    subjectPageCount,
    setSubjectPage,
    searchMessage,
    handleSearch
  } = useHomeLanding();

  return (
    <div className="site-shell">
      <header className="site-header">
        <div className="container nav-wrap">
          <a className="brand" href="#top" aria-label="Ptutor - Trang chủ">
            <img src={logo} alt="Ptutor" />
          </a>
          <button
            className="menu-button"
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'}
            aria-expanded={menuOpen}
          >
            <Icon name={menuOpen ? 'close' : 'menu'} size={25} />
          </button>
          <HomeNavigation isOpen={menuOpen} />
          <div className="nav-actions">
            <a className="login-link" href="#login">
              Đăng nhập
            </a>
            <a className="button button-small" href="#register">
              Đăng ký miễn phí
            </a>
          </div>
        </div>
      </header>
      <main>
        <section className="hero-section" id="top">
          <div className="hero-glow glow-one" />
          <div className="hero-glow glow-two" />
          <div className="container hero-layout">
            <div className="hero-copy">
              <div className="eyebrow">
                <span>✦</span> Học đúng người, tiến bộ mỗi ngày
              </div>
              <h1>
                Kết nối tri thức.
                <br />
                <span>Chạm tới tương lai.</span>
              </h1>
              <p className="hero-lead">
                Ptutor là nền tảng kết nối gia sư và học viên thông minh: đề xuất phù hợp bằng AI, đồng hành từ tìm lớp
                đến hợp đồng, buổi học và thanh toán.
              </p>
              <form className="search-box" onSubmit={handleSearch}>
                <label className="search-field">
                  <Icon name="search" size={21} />
                  <span>
                    <small>Bạn muốn học môn gì?</small>
                    <select name="subjectId" defaultValue="">
                      <option value="">Tất cả môn học</option>
                      {subjects.data?.map((subject) => (
                        <option key={subject.id} value={subject.id}>
                          {subject.name}
                        </option>
                      ))}
                    </select>
                  </span>
                </label>
                <span className="search-divider" />
                <label className="search-field grade-field">
                  <Icon name="book" size={21} />
                  <span>
                    <small>Bạn đang học lớp nào?</small>
                    <select name="gradeId" defaultValue="">
                      <option value="">Tất cả lớp</option>
                      {grades.data?.map((grade) => (
                        <option key={grade.id} value={grade.id}>
                          {grade.name}
                        </option>
                      ))}
                    </select>
                  </span>
                </label>
                <button className="button search-button" type="submit">
                  Tìm gia sư <Icon name="arrow" size={18} />
                </button>
              </form>
              <div className="search-feedback" aria-live="polite">
                {searchMessage}
              </div>
            </div>
            <HeroVisual />
          </div>
        </section>

        <section className="section subjects-section" id="subjects">
          <div className="container">
            <SectionHeading
              kicker="MÔN HỌC PHỔ BIẾN"
              title="Bạn muốn chinh phục môn nào?"
              description="Danh mục môn học được lấy trực tiếp từ Ptutor."
            />
            <ResourceError error={subjects.error} />
            <div className="subject-grid">
              {subjects.isLoading ? (
                <LoadingCards count={6} />
              ) : (
                visibleSubjects?.map((subject, index) => (
                  <SubjectCard subject={subject} index={index} key={subject.id} />
                ))
              )}
            </div>
            {subjectPageCount > 1 && (
              <div className="subject-pagination" aria-label="Phân trang môn học">
                <button
                  type="button"
                  disabled={currentSubjectPage === 0}
                  onClick={() => setSubjectPage((page) => Math.max(page - 1, 0))}
                >
                  ← Trước
                </button>
                <span>
                  {currentSubjectPage + 1} / {subjectPageCount}
                </span>
                <button
                  type="button"
                  disabled={currentSubjectPage >= subjectPageCount - 1}
                  onClick={() => setSubjectPage((page) => Math.min(page + 1, subjectPageCount - 1))}
                >
                  Tiếp →
                </button>
              </div>
            )}
          </div>
        </section>

        <section className="section tutor-section" id="tutors">
          <div className="container">
            <div className="section-heading split-heading">
              <div>
                <span className="section-kicker">BÀI ĐĂNG GIA SƯ</span>
                <h2>Những lớp học đang chờ bạn</h2>
              </div>
              <div>
                <p>Các bài đăng mới nhất từ gia sư trên nền tảng Ptutor.</p>
              </div>
            </div>
            <ResourceError error={posts.error} />
            <div className="tutor-grid">
              {posts.isLoading ? (
                <LoadingCards count={3} />
              ) : (
                posts.data?.map((post, index) => <TeachingPostCard post={post} index={index} key={post.id} />)
              )}
            </div>
          </div>
        </section>

        <section className="section features-section" id="features">
          <div className="container features-layout">
            <ProgressVisual />
            <div className="feature-copy">
              <span className="section-kicker">NỀN TẢNG HỌC TẬP TOÀN DIỆN</span>
              <h2>
                Từ kết nối phù hợp
                <br />
                đến học tập an tâm.
              </h2>
              <p>
                Ptutor hỗ trợ học viên, gia sư và quản trị viên quản lý trọn vẹn hành trình học tập trên cùng một nền
                tảng.
              </p>
              <div className="feature-list">
                {HOME_FEATURES.map((feature) => (
                  <div className="feature-item" key={feature.title}>
                    <span className="feature-icon">
                      <Icon name={feature.icon} size={24} />
                    </span>
                    <div>
                      <h3>{feature.title}</h3>
                      <p>{feature.text}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section className="section testimonials-section" id="reviews">
          <div className="container">
            <SectionHeading
              kicker="CÂU CHUYỆN TỪ CỘNG ĐỒNG"
              title="Học viên nói gì về Ptutor?"
              description="Những đánh giá mới nhất được chia sẻ từ cộng đồng học tập."
            />
            <ResourceError error={reviews.error} />
            <div className="testimonial-grid">
              {reviews.isLoading ? (
                <LoadingCards count={3} />
              ) : (
                reviews.data?.map((review, index) => (
                  <ReviewCard review={review} index={index} key={`${review.displayName}-${review.createdAt}`} />
                ))
              )}
            </div>
          </div>
        </section>

        <section className="cta-section" id="register">
          <div className="container cta-box">
            <span className="cta-bubble bubble-a" />
            <span className="cta-bubble bubble-b" />
            <span className="cta-bubble bubble-c" />
            <div>
              <span className="cta-kicker">BẮT ĐẦU HÀNH TRÌNH CỦA BẠN</span>
              <h2>
                Sẵn sàng học tốt hơn
                <br />
                cùng Ptutor?
              </h2>
              <p>Tạo tài khoản miễn phí và tìm người đồng hành phù hợp ngay hôm nay.</p>
            </div>
            <div className="cta-actions">
              <a className="button button-white" href="#student">
                Tôi muốn tìm gia sư <Icon name="arrow" size={18} />
              </a>
              <a className="button button-outline" href="#tutor-register">
                Tôi muốn trở thành gia sư
              </a>
            </div>
          </div>
        </section>
      </main>
      <footer className="site-footer">
        <div className="container footer-grid">
          <div className="footer-brand">
            <img src={logo} alt="Ptutor" />
            <p>Nền tảng kết nối gia sư và học viên thông minh, giúp việc học trở nên dễ dàng và hiệu quả hơn.</p>
          </div>
          <FooterLinks title="Dành cho học viên" links={['Tìm gia sư', 'Môn học', 'Cách hoạt động']} />
          <FooterLinks title="Dành cho gia sư" links={['Trở thành gia sư', 'Tìm lớp mới', 'Hướng dẫn gia sư']} />
          <FooterLinks title="Hỗ trợ" links={['Trung tâm trợ giúp', 'Liên hệ', 'Điều khoản sử dụng']} />
        </div>
        <div className="container footer-bottom">
          <p>© 2026 Ptutor. Kết nối tri thức, kiến tạo tương lai.</p>
          <p>
            <span /> Gia sư chất lượng · Học tập an tâm
          </p>
        </div>
      </footer>
      <button
        className={isBackToTopVisible ? 'back-to-top is-visible' : 'back-to-top'}
        type="button"
        onClick={scrollToTop}
        aria-label="Lên đầu trang"
      >
        <span aria-hidden="true">↑</span>
      </button>
    </div>
  );
}

function HeroVisual() {
  return (
    <div className="hero-visual" aria-label="Minh họa kết nối gia sư và học viên">
      <span className="hero-dot hero-dot-one" />
      <span className="hero-dot hero-dot-two" />
      <div className="hero-orbit" />
      <div className="hero-art-card home-hero-art">
        <div className="art-grid" />
        <div className="hero-book">
          <Icon name="book" size={75} />
        </div>
        <div className="hero-person person-tutor">
          <span>GS</span>
        </div>
        <div className="hero-person person-student">
          <span>HV</span>
        </div>
        <div className="hero-connection">
          <i />
          <i />
          <i />
        </div>
      </div>
      <div className="float-card float-rating">
        <span className="float-icon">★</span>
        <span>
          <strong>Được đánh giá cao</strong>
          <small>Từ cộng đồng học viên</small>
        </span>
      </div>
      <div className="float-card float-match">
        <span className="match-check">
          <Icon name="check" size={17} />
        </span>
        <span>
          <strong>Kết nối thành công!</strong>
          <small>Gia sư phù hợp với bạn</small>
        </span>
      </div>
      <div className="mini-lesson">
        <span className="lesson-icon">
          <Icon name="book" size={23} />
        </span>
        <span>
          <strong>Học tập linh hoạt</strong>
          <small>Theo lịch phù hợp với bạn</small>
        </span>
      </div>
    </div>
  );
}

function ProgressVisual() {
  return (
    <div className="feature-visual">
      <div className="feature-card-main">
        <div className="feature-card-top">
          <span className="feature-mini-logo">
            <Icon name="book" size={23} />
          </span>
          <span>
            <strong>Học tập hiệu quả</strong>
            <small>Tiến bộ qua từng buổi học</small>
          </span>
        </div>
        <div className="progress-label">
          <span>Mục tiêu tháng này</span>
          <strong>85%</strong>
        </div>
        <div className="progress-bar">
          <i />
        </div>
        <div className="week-row">
          <span>
            Tuần 1<i className="done">✓</i>
          </span>
          <span>
            Tuần 2<i className="done">✓</i>
          </span>
          <span>
            Tuần 3<i className="active">3</i>
          </span>
          <span>
            Tuần 4<i>4</i>
          </span>
        </div>
      </div>
      <div className="feature-badge">
        <span>↗</span>
        <div>
          <strong>Tiến bộ</strong>
          <small>qua từng buổi học</small>
        </div>
      </div>
      <span className="feature-shape one" />
      <span className="feature-shape two" />
    </div>
  );
}

function SectionHeading({ kicker, title, description }: { kicker: string; title: string; description: string }) {
  return (
    <div className="section-heading centered">
      <span className="section-kicker">{kicker}</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

function LoadingCards({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }, (_, index) => (
        <div className="loading-card" key={index} />
      ))}
    </>
  );
}

function ResourceError({ error }: { error: string | null }) {
  return error ? (
    <div className="api-error" role="alert">
      Không thể tải dữ liệu: {error}
    </div>
  ) : null;
}

function FooterLinks({ title, links }: { title: string; links: string[] }) {
  return (
    <div>
      <h3>{title}</h3>
      {links.map((link) => (
        <a href={`#${link}`} key={link}>
          {link}
        </a>
      ))}
    </div>
  );
}
