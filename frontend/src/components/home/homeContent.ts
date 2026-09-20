import type { IconName } from '../ui/Icon';

export interface HomeNavigationItem {
  label: string;
  href: string;
}

export interface HomeNavigationMenu {
  label: string;
  items: readonly HomeNavigationItem[];
}

export interface HomeFeature {
  icon: IconName;
  title: string;
  text: string;
}

export const HOME_NAVIGATION_MENUS: readonly HomeNavigationMenu[] = [
  {
    label: 'Tìm gia sư',
    items: [
      { label: 'Môn học phổ biến', href: '#subjects' },
      { label: 'Lớp gia sư', href: '#tutors' },
      { label: 'Đánh giá học viên', href: '#reviews' }
    ]
  },
  {
    label: 'Dành cho gia sư',
    items: [
      { label: 'Tìm học viên', href: '#tutors' },
      { label: 'Đăng yêu cầu dạy', href: '#register' }
    ]
  },
  {
    label: 'Khám phá',
    items: [
      { label: 'Tính năng nổi bật', href: '#features' },
      { label: 'Cách Ptutor hoạt động', href: '#features' }
    ]
  },
  {
    label: 'Hỗ trợ',
    items: [
      { label: 'Chatbot hỗ trợ', href: '#top' },
      { label: 'Liên hệ Ptutor', href: '#top' }
    ]
  }
];

export const HOME_FEATURES: readonly HomeFeature[] = [
  {
    icon: 'match',
    title: 'Đề xuất bằng AI',
    text: 'Gợi ý gia sư hoặc học viên phù hợp theo nhu cầu, lịch và tiêu chí tìm kiếm.'
  },
  {
    icon: 'contract',
    title: 'Hành trình học minh bạch',
    text: 'Quản lý yêu cầu, hợp đồng, buổi học và tiến độ tại một nơi.'
  },
  {
    icon: 'shield',
    title: 'Thanh toán an tâm',
    text: 'Ví điện tử, giao dịch, nhận tiền và hoàn tiền được theo dõi rõ ràng.'
  },
  {
    icon: 'support',
    title: 'Hỗ trợ nhanh chóng',
    text: 'Chatbot, thông báo, đánh giá và khiếu nại luôn sẵn sàng đồng hành.'
  }
];
