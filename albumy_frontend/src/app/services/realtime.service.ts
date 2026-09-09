import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable } from 'rxjs';
import { resolveWsUrl } from '../config/api.config';

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
  private subjects = new Map<number, BehaviorSubject<RealtimeMessage>>();
  private topics = new Map<number, string[]>();

  connect(): void {
    if (this.client) {
      return;
    }
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
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = null;
    this.subjects.clear();
    this.connected$.next(false);
  }

  onEvent(eventId: number): Observable<RealtimeMessage> {
    if (!this.subjects.has(eventId)) {
      this.subjects.set(eventId, new BehaviorSubject<RealtimeMessage>({} as RealtimeMessage));
      this.topics.set(eventId, [
        `/topic/events/${eventId}/photos`,
        `/topic/events/${eventId}/activity`,
        `/topic/events/${eventId}/stats`
      ]);
      if (this.client?.connected) {
        this.wire(eventId);
      }
    }
    return this.subjects.get(eventId)!.asObservable();
  }

  private wire(eventId: number): void {
    const client = this.client;
    if (!client || !client.connected) {
      return;
    }
    const topics = this.topics.get(eventId);
    if (!topics) {
      return;
    }
    for (const topic of topics) {
      try {
        client.subscribe(topic, (message: IMessage) => {
          try {
            const payload = JSON.parse(message.body) as RealtimeMessage;
            const subject = this.subjects.get(eventId);
            subject?.next(payload);
          } catch {
            // ignore malformed frames
          }
        });
      } catch {
        // subscription already exists or transport unavailable
      }
    }
  }
}