/**
 * Order contracts, mirroring order-service's DTOs (spec 11). The order
 * endpoints answer with plain JSON — no `ApiResponse` envelope.
 */

export type OrderStatus = 'CREATED' | 'CONFIRMED' | 'DELIVERED' | 'CANCELLED';

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED';

/** One line of an order (prices frozen at checkout). */
export interface OrderItem {
  id: number;
  productId: number;
  sku: string;
  name: string;
  imageUrl: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

/** The shipping address as frozen into the order. */
export interface OrderAddress {
  fullName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

/** A whole order. */
export interface Order {
  id: number;
  userId: number;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  items: OrderItem[];
  subtotal: number;
  shippingFee: number;
  total: number;
  shippingAddress: OrderAddress;
  createdAt: string;
  updatedAt: string;
}

/** One page of the order history (order-service's OrderPageResponse). */
export interface OrderPage {
  content: Order[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/** One payment attempt (payment-service's PaymentResponse). */
export interface Payment {
  id: number;
  orderId: number;
  userId: number;
  amount: number;
  method: PaymentMethod;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED';
  gatewayReference: string;
  failureReason: string;
  createdAt: string;
  updatedAt: string;
}

/** The mock gateway's payment methods. */
export type PaymentMethod = 'CARD' | 'UPI' | 'WALLET';
