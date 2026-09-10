import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { resolveWsUrl } from '../config/api.config';
import type { Client, IMessage } from '@stomp/stompjs';

export interface RealtimeMessage {
  eventId: number;
  type: string;
  topic: string;
  data?: any;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class RealtimeService {
  connected$ = new BehaviorSubject<boolean>(false);

  private client: Client | null = null;
  private connecting: Promise<void> | null = null;
  private subjects = new Map<number, BehaviorSubject<RealtimeMessage>>();
  private topics = new Map<number, string[]>();
  private subscriptionHeaders = new Map<number, Record<string, string>>();
  private subscriptions = new Map<number, { unsubscribe: () => void }[]>();

  connect(): void {
    if (this.client) {
      return;
    }
    if (!this.connecting) {
      this.connecting = this.establish();
    }
  }

  private async establish(): Promise<void> {
    try {
      const { Client } = await import('@stomp/stompjs');
      const client = new Client({
        brokerURL: resolveWsUrl(),
        reconnectDelay: 3000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000
      });

      client.onConnect = () => {
        this.connected$.next(true);
        for (const eventId of this.subjects.keys()) {
          this.wire(eventId);
        }
      };
      client.onWebSocketClose = () => {
        this.connected$.next(false);
      };
      client.onStompError = () => {
        this.connected$.next(false);
      };

      client.activate();
      this.client = client;
    } finally {
      this.connecting = null;
    }
  }

  onEvent(eventId: number, headers: Record<string, string> = {}): Observable<RealtimeMessage> {
    if (!this.subjects.has(eventId)) {
      this.subjects.set(eventId, new BehaviorSubject<RealtimeMessage>({} as RealtimeMessage));
      this.topics.set(eventId, [
        `/topic/events/${eventId}/photos`,
        `/topic/events/${eventId}/activity`
      ]);
      this.subscriptionHeaders.set(eventId, headers);
      if (!this.client) {
        this.connect();
      } else if (this.client?.connected) {
        this.wire(eventId);
      }
    } else if (headers) {
      this.subscriptionHeaders.set(eventId, headers);
    }
    return this.subjects.get(eventId)!.asObservable();
  }

  /** Unsubscribes the broker subscriptions for an event and drops the local
   *  subject/topics state. Call from ngOnDestroy of the owning component. */
  offEvent(eventId: number): void {
    this.unsubscribe(eventId);
    this.subjects.delete(eventId);
    this.topics.delete(eventId);
    this.subscriptionHeaders.delete(eventId);
    if (this.subjects.size === 0) {
      this.deactivate();
    }
  }

  private unsubscribe(eventId: number): void {
    const subs = this.subscriptions.get(eventId);
    if (subs) {
      for (const sub of subs) {
        try {
          sub.unsubscribe();
        } catch {
          // subscription was already torn down (e.g. socket closed)
        }
      }
    }
    this.subscriptions.delete(eventId);
  }

  private deactivate(): void {
    const client = this.client;
    if (client) {
      try {
        client.deactivate();
      } catch {
        // client already torn down
      }
    }
    this.client = null;
    this.subscriptions.clear();
  }

  private wire(eventId: number): void {
    const client = this.client;
    if (!client || !client.connected) {
      return;
    }
    this.unsubscribe(eventId);
    const topics = this.topics.get(eventId);
    if (!topics) {
      return;
    }
    const headers = this.subscriptionHeaders.get(eventId) || {};
    const subs: { unsubscribe: () => void }[] = [];
    for (const topic of topics) {
      try {
        subs.push(client.subscribe(topic, (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as RealtimeMessage;
            const subject = this.subjects.get(eventId);
            subject?.next(payload);
          } catch {
            // ignore malformed frames
          }
        }, headers));
      } catch {
        // subscription already exists or transport unavailable
      }
    }
    this.subscriptions.set(eventId, subs);
  }
}