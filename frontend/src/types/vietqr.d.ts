declare module 'vietqr' {
  export class VietQR {
    genQRCodeBase64(input: {
      bank: string;
      accountName: string;
      accountNumber: string;
      amount: string;
      memo: string;
      media: 'png' | 'jpg' | 'svg';
    }): Promise<{
      data: {
        code: string;
        desc: string;
        data?: { qrDataURL: string; qrCode: string };
      };
    }>;
  }
}
