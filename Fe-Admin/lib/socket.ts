import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:3000';

export class SocketClient {
  private client: Client;
  private endpoint: string;

  constructor(servicePath: string) {
    this.endpoint = `${GATEWAY_URL}${servicePath}`;
    
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
      console.log(`Connected to STOMP via ${this.endpoint}`);
      onConnect();
    };

    if (onError) {
      this.client.onStompError = onError;
      this.client.onWebSocketError = onError;
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

// Singleton instances for different services
export const paymentSocket = new SocketClient('/ws/payment');
export const kitchenSocket = new SocketClient('/ws/kitchen');
export const orderSocket = new SocketClient('/ws/order');
