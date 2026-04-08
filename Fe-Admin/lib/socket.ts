import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// ✅ FIX: Use direct service URLs for WebSocket (Spring Cloud Gateway doesn't proxy WebSocket upgrade)
// HTTP calls go through GATEWAY_URL, but WebSocket calls must go directly to services
const PAYMENT_SERVICE_URL = process.env.NEXT_PUBLIC_PAYMENT_SERVICE_URL || 'http://localhost:3008';
const KITCHEN_SERVICE_URL = process.env.NEXT_PUBLIC_KITCHEN_SERVICE_URL || 'http://localhost:3004';
const ORDER_SERVICE_URL = process.env.NEXT_PUBLIC_ORDER_SERVICE_URL || 'http://localhost:3003';

export class SocketClient {
  private client: Client;
  private endpoint: string;
  private servicePath: string;

  constructor(baseUrl: string, servicePath: string) {
    this.endpoint = `${baseUrl}${servicePath}`;
    this.servicePath = servicePath;
    
    this.client = new Client({
      webSocketFactory: () => new SockJS(this.endpoint),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        if (process.env.NODE_ENV !== 'production') {
          console.log(`[STOMP ${servicePath}]`, str);
        }
      },
    });
  }

  public connect(onConnect: () => void, onError?: (err: any) => void) {
    this.client.onConnect = () => {
      console.log(`✅ Connected to STOMP: ${this.endpoint}`);
      onConnect();
    };

    // Nếu đã connected sẵn (singleton dùng lại giữa các page), chạy setup ngay
    if (this.client.active) {
      onConnect();
      return;
    }

    if (onError) {
      this.client.onStompError = (error) => {
        console.error(`❌ STOMP Error (${this.servicePath}):`, error);
        onError(error);
      };
      this.client.onWebSocketError = (error) => {
        console.error(`❌ WebSocket Error (${this.servicePath}):`, error);
        onError(error);
      };
    }

    this.client.activate();
  }

  public disconnect() {
    if (this.client.active) {
      this.client.deactivate();
    }
  }

  public subscribe(topic: string, callback: (message: any) => void) {
    return this.client.subscribe(topic, (message) => {
      if (message.body) {
        callback(JSON.parse(message.body));
      } else {
        callback(null);
      }
    });
  }
}

// ✅ Singleton instances for different services - using DIRECT service URLs
export const paymentSocket = new SocketClient(PAYMENT_SERVICE_URL, '/ws/payment');
export const kitchenSocket = new SocketClient(KITCHEN_SERVICE_URL, '/ws/kitchen');
export const orderSocket = new SocketClient(ORDER_SERVICE_URL, '/ws/order');
