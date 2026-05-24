export default function Loading() {
  return (
    <main className="grid min-h-screen place-items-center bg-sand px-5 text-center dark:bg-night">
      <div className="glass-panel rounded-[2rem] p-8">
        <img
          alt="شعار نطاق"
          className="mx-auto size-20 rounded-3xl object-contain shadow-glow"
          src="/brand/nitaaq-logo.png"
        />
        <h1 className="mt-5 text-2xl font-black text-slate-950 dark:text-white">
          نطاق | الباحث العام
        </h1>
        <p className="mt-2 text-sm font-semibold text-slate-500 dark:text-slate-300">
          يتم تجهيز تجربة البحث الآمن...
        </p>
      </div>
    </main>
  );
}
