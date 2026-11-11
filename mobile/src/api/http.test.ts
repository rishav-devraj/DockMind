import { ApiError, NetworkError } from './errors';
import { HttpApiClient } from './http';

const respond = (status: number, body: string) =>
  ({ ok: status >= 200 && status < 300, status, text: async () => body }) as Response;

describe('HttpApiClient', () => {
  it('sends the idempotency key and reports a replay from the 200 status', async () => {
    const fetchImpl = jest.fn(async () => respond(200, '{"id":"RCP-1"}'));
    const client = new HttpApiClient('http://host:8080/', fetchImpl);

    const result = await client.uploadReceipt({
      imageUri: 'file:///a.jpg',
      idempotencyKey: 'key-1',
      fixture: 'note-2',
    });

    expect(result.replayed).toBe(true);
    const [url, init] = fetchImpl.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('http://host:8080/receipts');
    expect(init.headers).toMatchObject({ 'Idempotency-Key': 'key-1', 'X-Fixture': 'note-2' });
  });

  it('maps the contract error body to ApiError', async () => {
    const client = new HttpApiClient('http://h', async () =>
      respond(409, '{"error":"TERMINAL","message":"Already decided."}'),
    );
    const error = await client.confirmReceipt('RCP-1').catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, code: 'TERMINAL', message: 'Already decided.' });
  });

  it('maps a failed fetch to NetworkError', async () => {
    const client = new HttpApiClient('http://h', async () => {
      throw new TypeError('Network request failed');
    });
    await expect(client.getReceipt('RCP-1')).rejects.toBeInstanceOf(NetworkError);
  });
});
