import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts';

const data = [
  { name: 'Jan', value: 400 },
  { name: 'Feb', value: 300 },
  { name: 'Mar', value: 600 },
  { name: 'Apr', value: 800 },
];

const pieData = [
  { name: 'Paid', value: 400 },
  { name: 'Warranty', value: 300 },
  { name: 'Internal', value: 300 },
];

const COLORS = ['#4f46e5', '#10b981', '#f59e0b'];

export default function Dashboard() {
  return (
    <div className="space-y-6">
      <h2 className="text-2xl font-bold">Dashboard Overview</h2>
      
      <div className="grid grid-cols-1 gap-6 md:grid-cols-4">
        {['Total Turnover', 'Job Card Count', 'Avg per JC', 'Top Station'].map((title, i) => (
          <div key={title} className="rounded-xl bg-[var(--color-surface)] p-6 shadow-md transition hover:scale-[1.02]">
            <h3 className="text-sm text-gray-400">{title}</h3>
            <p className="mt-2 text-3xl font-bold text-white">
              {i === 3 ? 'INDORE' : i === 2 ? '₹4,520' : i === 1 ? '1,204' : '₹5.4M'}
            </p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-xl bg-[var(--color-surface)] p-6 shadow-md">
          <h3 className="mb-4 text-lg font-semibold text-white">Monthly Turnover</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data}>
                <XAxis dataKey="name" stroke="#9ca3af" />
                <YAxis stroke="#9ca3af" />
                <Tooltip cursor={{fill: '#334155'}} contentStyle={{backgroundColor: '#1e293b', border: 'none'}} />
                <Bar dataKey="value" fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="rounded-xl bg-[var(--color-surface)] p-6 shadow-md">
          <h3 className="mb-4 text-lg font-semibold text-white">Billing Type Split</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={80} paddingAngle={5} dataKey="value">
                  {pieData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip contentStyle={{backgroundColor: '#1e293b', border: 'none'}} />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  );
}
